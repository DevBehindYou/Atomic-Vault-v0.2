package com.example.keystore

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The envelope migration against the real EncryptedSharedPreferences and the
 * real Android Keystore (the JVM tests use fakes for both).
 */
@RunWith(AndroidJUnit4::class)
class VaultMetaStoreDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = listOf("atomicvault_meta", "atomicvault_meta_fallback", "atomicvault_meta_marker", "atomicvault_meta_sealed")

    @Before
    @After
    fun clean() {
        names.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        VaultMetaStore.openEncrypted(context).edit().clear().commit()
    }

    @Test
    fun anEnvelopeInEncryptedSharedPreferencesMovesToTheSealedLayout() {
        val salt = "c2FsdHNhbHRzYWx0c2FsdA=="
        val kdf = VaultMetaStore.createDefaultKdfParamsJson(salt)
        val wrapped = ByteArray(60) { (it * 13).toByte() }

        // How every version before 0.4.0 stored it.
        VaultMetaStore.openEncrypted(context).edit()
            .putBoolean("has_vault", true)
            .putString("salt_b64", salt)
            .putString("kdf_params_json", kdf)
            .putString("wrapped_dek_b64", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putInt("scheme_version", 2)
            .commit()
        context.getSharedPreferences("atomicvault_meta_marker", Context.MODE_PRIVATE).edit()
            .putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()

        val migrated = VaultMetaStore(context)
        assertFalse(migrated.isUnavailable)
        assertEquals(VaultMetaStore.KIND_SEALED, migrated.kind)
        assertArrayEquals(wrapped, migrated.getVaultEnvelope()!!.wrappedDek)
        assertFalse(VaultMetaStore.openEncrypted(context).contains("wrapped_dek_b64"))

        // A new process sees the same envelope through the Keystore key.
        val reopened = VaultMetaStore(context)
        val envelope = reopened.getVaultEnvelope()!!
        assertEquals(salt, envelope.saltBase64)
        assertEquals(kdf, envelope.kdfParamsJson)
        assertArrayEquals(wrapped, envelope.wrappedDek)
        assertEquals(2, envelope.schemeVersion)
    }

    @Test
    fun aFreshInstallSealsAndReadsBack() {
        val store = VaultMetaStore(context)
        assertEquals(VaultMetaStore.KIND_SEALED, store.kind)
        val wrapped = ByteArray(60) { 3 }
        store.saveVaultEnvelope("c2FsdA==", "{}", wrapped)
        assertArrayEquals(wrapped, VaultMetaStore(context).getVaultEnvelope()!!.wrappedDek)
    }
}
