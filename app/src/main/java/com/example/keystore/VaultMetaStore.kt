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
 * this store decides where it lives and keeps it bound to the device.
 *
 * Current layout ("sealed"): one entry in plain private preferences, sealed
 * with this app's own Keystore AES key ([EnvelopeSealer]). Earlier versions
 * kept it in EncryptedSharedPreferences (androidx.security-crypto, now
 * deprecated) or, when that failed on first use, in plain preferences. Both
 * migrate to the sealed layout once, on open: copy, read back and compare,
 * switch the marker, and only then clear the old copy. Any failure leaves
 * the old store in use and the migration is tried again on the next launch.
 *
 * The choice is remembered per install. A store that worked before and
 * cannot be read today reports [isUnavailable] instead of falling back to an
 * empty one: an empty store would make the app show onboarding over a real
 * vault.
 */
// commit(), not apply(): the store kind and the key envelope must be on disk
// before the vault database is created or opened, never written later.
@android.annotation.SuppressLint("ApplySharedPref")
class VaultMetaStore internal constructor(
    app: Context,
    openLegacyEncrypted: () -> SharedPreferences?,
    existingSealer: () -> EnvelopeSealer?,
    newSealer: () -> EnvelopeSealer?
) {

    constructor(context: Context) : this(
        context.applicationContext,
        openLegacyEncrypted = {
            try {
                openEncrypted(context.applicationContext)
            } catch (e: Exception) {
                null
            }
        },
        existingSealer = { KeystoreEnvelopeSealer.existing() },
        newSealer = { KeystoreEnvelopeSealer.existingOrNew() }
    )

    private val marker = app.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
    private val sealedPrefs = app.getSharedPreferences(SEALED_PREFS, Context.MODE_PRIVATE)
    private val backend: Backend?

    /** True when the store this install uses cannot be opened right now. */
    val isUnavailable: Boolean get() = backend == null

    /** Which layout is in use (for tests and diagnostics). */
    internal val kind: String? get() = marker.getString(KEY_KIND, null)

    init {
        val plain = app.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)

        fun migrate(legacy: PrefsBackend): Backend {
            if (!legacy.hasVault()) {
                // Nothing to carry over: start using the sealed layout.
                val sealer = newSealer() ?: return legacy
                if (!marker.edit().putString(KEY_KIND, KIND_SEALED).commit()) return legacy
                legacy.clear()
                return SealedBackend(sealedPrefs, sealer)
            }
            val envelope = legacy.read() ?: return legacy // incomplete entry: leave it alone
            val sealer = newSealer() ?: return legacy
            val target = SealedBackend(sealedPrefs, sealer)
            return try {
                check(target.write(envelope)) { "write failed" }
                check(target.read()?.sameAs(envelope) == true) { "read-back differs" }
                check(marker.edit().putString(KEY_KIND, KIND_SEALED).commit()) { "marker not saved" }
                legacy.clear()
                target
            } catch (e: Exception) {
                target.clear()
                legacy
            }
        }

        backend = when (marker.getString(KEY_KIND, null)) {
            KIND_SEALED -> {
                val sealer = existingSealer()
                when {
                    sealer != null -> SealedBackend(sealedPrefs, sealer).takeIf { it.isReadable() }
                    // The key is gone but the envelope is here: it can never be
                    // opened again on this install. Report it; never start over.
                    sealedPrefs.contains(KEY_SEALED) -> null
                    else -> newSealer()?.let { SealedBackend(sealedPrefs, it) }
                }
            }
            KIND_ENCRYPTED -> openLegacyEncrypted()?.let { migrate(PrefsBackend(it)) } // null => unavailable
            KIND_PLAIN -> migrate(PrefsBackend(plain))
            else -> {
                // First launch of this version (or a fresh install): adopt
                // whichever store actually holds the vault, and remember it.
                val encrypted = openLegacyEncrypted()
                val vaultFileExists = com.example.database.VaultDatabase.getDatabaseFile(app).exists()
                when {
                    encrypted != null && hasVaultIn(encrypted) -> {
                        marker.edit().putString(KEY_KIND, KIND_ENCRYPTED).commit()
                        migrate(PrefsBackend(encrypted))
                    }
                    hasVaultIn(plain) -> {
                        marker.edit().putString(KEY_KIND, KIND_PLAIN).commit()
                        migrate(PrefsBackend(plain))
                    }
                    // A vault database exists but no envelope was found and
                    // the encrypted store would not open: the envelope is most
                    // likely in there. Decide nothing; try again next launch.
                    encrypted == null && vaultFileExists -> null
                    else -> {
                        val sealer = newSealer()
                        if (sealer != null) {
                            marker.edit().putString(KEY_KIND, KIND_SEALED).commit()
                            SealedBackend(sealedPrefs, sealer)
                        } else {
                            // No usable Keystore at all: the envelope is still
                            // ciphertext under the Argon2 key.
                            marker.edit().putString(KEY_KIND, KIND_PLAIN).commit()
                            PrefsBackend(plain)
                        }
                    }
                }
            }
        }
    }

    fun hasVault(): Boolean = backend?.hasVault() ?: false

    /** Written synchronously: the database is created right after, and must never exist without its envelope. */
    fun saveVaultEnvelope(saltBase64: String, kdfParamsJson: String, wrappedDek: ByteArray) {
        val store = checkNotNull(backend) { "The vault key store is unavailable" }
        val ok = store.write(VaultEnvelope(saltBase64, kdfParamsJson, wrappedDek, SchemeVersion.SCHEME_VERSION.toInt()))
        check(ok) { "Could not save the vault key envelope" }
    }

    fun getVaultEnvelope(): VaultEnvelope? = backend?.read()

    fun clear() {
        backend?.clear()
    }

    /** One place the envelope can live. Writes are synchronous. */
    private interface Backend {
        fun hasVault(): Boolean
        fun read(): VaultEnvelope?
        fun write(envelope: VaultEnvelope): Boolean
        fun clear()
    }

    /** The pre-0.4.0 layout: one preference per field, in plain or EncryptedSharedPreferences. */
    private class PrefsBackend(private val p: SharedPreferences) : Backend {
        override fun hasVault() = hasVaultIn(p)

        override fun read(): VaultEnvelope? {
            if (!hasVaultIn(p)) return null
            return VaultEnvelope(
                saltBase64 = p.getString("salt_b64", null) ?: return null,
                kdfParamsJson = p.getString("kdf_params_json", null) ?: return null,
                wrappedDek = Base64.decode(p.getString("wrapped_dek_b64", null) ?: return null, Base64.NO_WRAP),
                schemeVersion = p.getInt("scheme_version", 1)
            )
        }

        override fun write(envelope: VaultEnvelope): Boolean = p.edit()
            .putBoolean("has_vault", true)
            .putString("salt_b64", envelope.saltBase64)
            .putString("kdf_params_json", envelope.kdfParamsJson)
            .putString("wrapped_dek_b64", Base64.encodeToString(envelope.wrappedDek, Base64.NO_WRAP))
            .putInt("scheme_version", envelope.schemeVersion)
            .commit()

        override fun clear() {
            p.edit().clear().commit()
        }
    }

    /** The 0.4.0 layout: the whole envelope as one JSON value, sealed under the Keystore. */
    private class SealedBackend(private val p: SharedPreferences, private val sealer: EnvelopeSealer) : Backend {
        override fun hasVault() = p.contains(KEY_SEALED)

        fun isReadable(): Boolean = !hasVault() || try {
            read() != null
        } catch (e: Exception) {
            false
        }

        override fun read(): VaultEnvelope? {
            val sealed = p.getString(KEY_SEALED, null) ?: return null
            val json = JSONObject(String(sealer.open(sealed), Charsets.UTF_8))
            return VaultEnvelope(
                saltBase64 = json.getString("salt_b64"),
                kdfParamsJson = json.getString("kdf_params_json"),
                wrappedDek = Base64.decode(json.getString("wrapped_dek_b64"), Base64.NO_WRAP),
                schemeVersion = json.getInt("scheme_version")
            )
        }

        override fun write(envelope: VaultEnvelope): Boolean {
            val json = JSONObject()
                .put("salt_b64", envelope.saltBase64)
                .put("kdf_params_json", envelope.kdfParamsJson)
                .put("wrapped_dek_b64", Base64.encodeToString(envelope.wrappedDek, Base64.NO_WRAP))
                .put("scheme_version", envelope.schemeVersion)
            return p.edit().putString(KEY_SEALED, sealer.seal(json.toString().toByteArray(Charsets.UTF_8))).commit()
        }

        override fun clear() {
            p.edit().clear().commit()
        }
    }

    companion object {
        private const val MARKER_PREFS = "atomicvault_meta_marker"
        private const val PLAIN_PREFS = "atomicvault_meta_fallback"
        private const val SEALED_PREFS = "atomicvault_meta_sealed"
        private const val KEY_SEALED = "envelope_v1"
        private const val KEY_KIND = "store_kind"
        internal const val KIND_ENCRYPTED = "encrypted"
        internal const val KIND_PLAIN = "plain"
        internal const val KIND_SEALED = "sealed"

        private fun hasVaultIn(p: SharedPreferences): Boolean =
            p.getBoolean("has_vault", false) && p.getString("wrapped_dek_b64", null) != null

        private fun VaultEnvelope.sameAs(other: VaultEnvelope): Boolean =
            saltBase64 == other.saltBase64 &&
                kdfParamsJson == other.kdfParamsJson &&
                wrappedDek.contentEquals(other.wrappedDek) &&
                schemeVersion == other.schemeVersion

        /** The legacy store, read only to migrate away from it. */
        internal fun openEncrypted(context: Context): SharedPreferences {
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
