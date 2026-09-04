package com.example.keystore

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.crypto.SchemeVersion
import org.json.JSONObject

data class VaultEnvelope(
    val saltBase64: String,
    val kdfParamsJson: String,
    val wrappedDek: ByteArray,
    val schemeVersion: Int
)

class VaultMetaStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "atomicvault_meta",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("atomicvault_meta_fallback", Context.MODE_PRIVATE)
        }
    }

    fun hasVault(): Boolean {
        return prefs.getBoolean("has_vault", false) && prefs.getString("wrapped_dek_b64", null) != null
    }

    fun saveVaultEnvelope(saltBase64: String, kdfParamsJson: String, wrappedDek: ByteArray) {
        val wrappedDekB64 = Base64.encodeToString(wrappedDek, Base64.NO_WRAP)
        prefs.edit()
            .putBoolean("has_vault", true)
            .putString("salt_b64", saltBase64)
            .putString("kdf_params_json", kdfParamsJson)
            .putString("wrapped_dek_b64", wrappedDekB64)
            .putInt("scheme_version", SchemeVersion.SCHEME_VERSION.toInt())
            .apply()
    }

    fun getVaultEnvelope(): VaultEnvelope? {
        if (!hasVault()) return null
        val saltB64 = prefs.getString("salt_b64", null) ?: return null
        val kdfParams = prefs.getString("kdf_params_json", null) ?: return null
        val wrappedDekB64 = prefs.getString("wrapped_dek_b64", null) ?: return null
        val version = prefs.getInt("scheme_version", 1)
        val wrappedDek = Base64.decode(wrappedDekB64, Base64.NO_WRAP)
        return VaultEnvelope(
            saltBase64 = saltB64,
            kdfParamsJson = kdfParams,
            wrappedDek = wrappedDek,
            schemeVersion = version
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun createDefaultKdfParamsJson(saltBase64: String): String {
            val obj = JSONObject()
            obj.put("algorithm", "argon2id")
            obj.put("memoryKiB", 65536)
            obj.put("iterations", 3)
            obj.put("parallelism", 1)
            obj.put("salt", saltBase64)
            return obj.toString()
        }
    }
}
