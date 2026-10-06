package com.example.keystore

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.crypto.Argon2Kdf
import com.example.crypto.SchemeVersion
import org.json.JSONObject

data class VaultEnvelope(
    val saltBase64: String,
    val kdfParamsJson: String,
    val wrappedDek: ByteArray,
    val schemeVersion: Int
)

/** Argon2id cost parameters, as stored with the vault that was created with them. */
data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        val DEFAULT = KdfParams(Argon2Kdf.DEFAULT_MEMORY_KIB, Argon2Kdf.DEFAULT_ITERATIONS, Argon2Kdf.DEFAULT_PARALLELISM)

        /**
         * Reads the parameters a vault was created with. Unlock must use these,
         * not the current defaults, or tuning the defaults would lock out every
         * existing vault. Missing fields fall back to the defaults the app has
         * always used; anything that is not Argon2id or out of sane bounds is
         * rejected rather than guessed.
         */
        fun parse(json: String?): KdfParams {
            if (json.isNullOrBlank()) return DEFAULT
            val obj = JSONObject(json)
            val algorithm = obj.optString("algorithm", "argon2id")
            require(algorithm.equals("argon2id", ignoreCase = true)) { "Unsupported key derivation: $algorithm" }
            val params = KdfParams(
                memoryKiB = obj.optInt("memoryKiB", DEFAULT.memoryKiB),
                iterations = obj.optInt("iterations", DEFAULT.iterations),
                parallelism = obj.optInt("parallelism", DEFAULT.parallelism)
            )
            require(params.memoryKiB in 8_192..1_048_576) { "Invalid Argon2 memory: ${params.memoryKiB} KiB" }
            require(params.iterations in 1..32) { "Invalid Argon2 iterations: ${params.iterations}" }
            require(params.parallelism in 1..16) { "Invalid Argon2 parallelism: ${params.parallelism}" }
            return params
        }
    }
}

/**
 * Where the vault envelope (salt, KDF parameters, password-wrapped data key)
 * is kept. The envelope is already ciphertext under the Argon2-derived key;
 * this store only decides which preferences file holds it.
 *
 * The choice is made ONCE per install and remembered. Previously, if
 * EncryptedSharedPreferences failed to open on some launch (a Keystore
 * hiccup, a restored device), the class silently switched to an empty plain
 * file: the app then believed no vault existed, showed onboarding over the
 * real encrypted database, and creating a vault failed. Now a store that
 * worked before and fails today reports [isUnavailable] instead, and the app
 * shows an error rather than onboarding.
 */
// commit(), not apply(): the store kind and the key envelope must be on disk
// before the vault database is created or opened, never written later.
@android.annotation.SuppressLint("ApplySharedPref")
class VaultMetaStore(context: Context) {

    private val prefs: SharedPreferences?

    /** True when the store this install uses cannot be opened right now. */
    val isUnavailable: Boolean get() = prefs == null

    init {
        val app = context.applicationContext
        val marker = app.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
        val plain = app.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
        val encrypted = try {
            openEncrypted(app)
        } catch (e: Exception) {
            null
        }

        prefs = when (marker.getString(KEY_KIND, null)) {
            KIND_ENCRYPTED -> encrypted // null => unavailable; never fall back silently
            KIND_PLAIN -> plain
            else -> {
                // First launch of this version (or a fresh install): adopt
                // whichever store actually holds the vault, else prefer the
                // encrypted one, and remember the choice.
                val vaultFileExists = com.example.database.VaultDatabase.getDatabaseFile(app).exists()
                val chosen = when {
                    encrypted != null && hasVaultIn(encrypted) -> KIND_ENCRYPTED
                    hasVaultIn(plain) -> KIND_PLAIN
                    // A vault database exists but neither store shows its
                    // envelope and the encrypted one would not open: that
                    // envelope is most likely in the encrypted store. Decide
                    // nothing yet; report unavailable and try again next launch.
                    encrypted == null && vaultFileExists -> null
                    encrypted != null -> KIND_ENCRYPTED
                    else -> KIND_PLAIN
                }
                if (chosen != null) marker.edit().putString(KEY_KIND, chosen).commit()
                when (chosen) {
                    KIND_ENCRYPTED -> encrypted
                    KIND_PLAIN -> plain
                    else -> null
                }
            }
        }
    }

    fun hasVault(): Boolean = prefs?.let { hasVaultIn(it) } ?: false

    /** Written synchronously: the database is created right after, and must never exist without its envelope. */
    fun saveVaultEnvelope(saltBase64: String, kdfParamsJson: String, wrappedDek: ByteArray) {
        val store = checkNotNull(prefs) { "The vault key store is unavailable" }
        val wrappedDekB64 = Base64.encodeToString(wrappedDek, Base64.NO_WRAP)
        val ok = store.edit()
            .putBoolean("has_vault", true)
            .putString("salt_b64", saltBase64)
            .putString("kdf_params_json", kdfParamsJson)
            .putString("wrapped_dek_b64", wrappedDekB64)
            .putInt("scheme_version", SchemeVersion.SCHEME_VERSION.toInt())
            .commit()
        check(ok) { "Could not save the vault key envelope" }
    }

    fun getVaultEnvelope(): VaultEnvelope? {
        val store = prefs ?: return null
        if (!hasVaultIn(store)) return null
        val saltB64 = store.getString("salt_b64", null) ?: return null
        val kdfParams = store.getString("kdf_params_json", null) ?: return null
        val wrappedDekB64 = store.getString("wrapped_dek_b64", null) ?: return null
        val version = store.getInt("scheme_version", 1)
        return VaultEnvelope(
            saltBase64 = saltB64,
            kdfParamsJson = kdfParams,
            wrappedDek = Base64.decode(wrappedDekB64, Base64.NO_WRAP),
            schemeVersion = version
        )
    }

    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }

    companion object {
        private const val MARKER_PREFS = "atomicvault_meta_marker"
        private const val PLAIN_PREFS = "atomicvault_meta_fallback"
        private const val KEY_KIND = "store_kind"
        private const val KIND_ENCRYPTED = "encrypted"
        private const val KIND_PLAIN = "plain"

        private fun hasVaultIn(p: SharedPreferences): Boolean =
            p.getBoolean("has_vault", false) && p.getString("wrapped_dek_b64", null) != null

        private fun openEncrypted(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "atomicvault_meta",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun createDefaultKdfParamsJson(saltBase64: String): String {
            val obj = JSONObject()
            obj.put("algorithm", "argon2id")
            obj.put("memoryKiB", KdfParams.DEFAULT.memoryKiB)
            obj.put("iterations", KdfParams.DEFAULT.iterations)
            obj.put("parallelism", KdfParams.DEFAULT.parallelism)
            obj.put("salt", saltBase64)
            return obj.toString()
        }
    }
}
