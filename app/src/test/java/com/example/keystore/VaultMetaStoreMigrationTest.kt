package com.example.keystore

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.database.VaultDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The move of the vault envelope from EncryptedSharedPreferences (or the
 * plain fallback) to the Keystore-sealed layout. A mistake here locks people
 * out of their vault, so every path is covered, including the failures that
 * must leave the old copy in place.
 */
@RunWith(RobolectricTestRunner::class)
class VaultMetaStoreMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val salt = "c2FsdHNhbHRzYWx0c2FsdA=="
    private val kdf = VaultMetaStore.createDefaultKdfParamsJson(salt)
    private val wrapped = ByteArray(60) { (it * 7).toByte() }

    /** Reversible stand-in for the Keystore sealer, with switchable failures. */
    private class FakeSealer(var failSeal: Boolean = false, var corruptOpen: Boolean = false) : EnvelopeSealer {
        override fun seal(plain: ByteArray): String {
            check(!failSeal) { "seal failed" }
            return "fake:" + Base64.encodeToString(plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray(), Base64.NO_WRAP)
        }

        override fun open(sealed: String): ByteArray {
            require(sealed.startsWith("fake:"))
            val bytes = Base64.decode(sealed.removePrefix("fake:"), Base64.NO_WRAP).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            return if (corruptOpen) bytes.toString(Charsets.UTF_8).replace(salt, "different").toByteArray() else bytes
        }
    }

    private val legacyEncrypted: SharedPreferences get() = context.getSharedPreferences("fake_esp", Context.MODE_PRIVATE)
    private val legacyPlain: SharedPreferences get() = context.getSharedPreferences("atomicvault_meta_fallback", Context.MODE_PRIVATE)
    private val marker: SharedPreferences get() = context.getSharedPreferences("atomicvault_meta_marker", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        listOf("fake_esp", "atomicvault_meta_fallback", "atomicvault_meta_marker", "atomicvault_meta_sealed").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        VaultDatabase.getDatabaseFile(context).delete()
    }

    private fun store(
        sealer: FakeSealer? = FakeSealer(),
        keyExists: Boolean = true,
        espOpens: Boolean = true
    ) = VaultMetaStore(
        context,
        openLegacyEncrypted = { if (espOpens) legacyEncrypted else null },
        existingSealer = { if (keyExists) sealer else null },
        newSealer = { sealer }
    )

    private fun writeLegacy(p: SharedPreferences) {
        p.edit()
            .putBoolean("has_vault", true)
            .putString("salt_b64", salt)
            .putString("kdf_params_json", kdf)
            .putString("wrapped_dek_b64", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putInt("scheme_version", 2)
            .commit()
    }

    private fun assertEnvelope(s: VaultMetaStore) {
        val e = s.getVaultEnvelope()!!
        assertEquals(salt, e.saltBase64)
        assertEquals(kdf, e.kdfParamsJson)
        assertArrayEquals(wrapped, e.wrappedDek)
        assertEquals(2, e.schemeVersion)
    }

    @Test
    fun `a fresh install uses the sealed layout`() {
        val s = store()
        assertEquals(VaultMetaStore.KIND_SEALED, s.kind)
        assertFalse(s.hasVault())
        s.saveVaultEnvelope(salt, kdf, wrapped)
        val again = store()
        assertTrue(again.hasVault())
        assertArrayEquals(wrapped, again.getVaultEnvelope()!!.wrappedDek)
    }

    @Test
    fun `an encrypted-store vault moves to the sealed layout and the old copy is cleared`() {
        writeLegacy(legacyEncrypted)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()
        val s = store()
        assertFalse(s.isUnavailable)
        assertEquals(VaultMetaStore.KIND_SEALED, s.kind)
        assertEnvelope(s)
        assertFalse(legacyEncrypted.contains("wrapped_dek_b64"))
        assertEnvelope(store()) // and it is still there next launch
    }

    @Test
    fun `a vault found in the encrypted store on first launch of this version moves too`() {
        writeLegacy(legacyEncrypted)
        val s = store()
        assertEquals(VaultMetaStore.KIND_SEALED, s.kind)
        assertEnvelope(s)
    }

    @Test
    fun `a plain-fallback vault moves to the sealed layout`() {
        writeLegacy(legacyPlain)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_PLAIN).commit()
        val s = store()
        assertEquals(VaultMetaStore.KIND_SEALED, s.kind)
        assertEnvelope(s)
        assertFalse(legacyPlain.contains("wrapped_dek_b64"))
    }

    @Test
    fun `a failed write keeps the old store and migrates on a later launch`() {
        writeLegacy(legacyEncrypted)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()
        val failing = store(sealer = FakeSealer(failSeal = true))
        assertFalse(failing.isUnavailable)
        assertEquals(VaultMetaStore.KIND_ENCRYPTED, failing.kind)
        assertEnvelope(failing)
        assertTrue(legacyEncrypted.contains("wrapped_dek_b64"))

        val later = store()
        assertEquals(VaultMetaStore.KIND_SEALED, later.kind)
        assertEnvelope(later)
    }

    @Test
    fun `a read-back that differs keeps the old store`() {
        writeLegacy(legacyEncrypted)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()
        val s = store(sealer = FakeSealer(corruptOpen = true))
        assertEquals(VaultMetaStore.KIND_ENCRYPTED, s.kind)
        assertEnvelope(s)
        assertTrue(legacyEncrypted.contains("wrapped_dek_b64"))
    }

    @Test
    fun `no Keystore at migration time keeps the old store`() {
        writeLegacy(legacyEncrypted)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()
        val s = store(sealer = null, keyExists = false)
        assertEquals(VaultMetaStore.KIND_ENCRYPTED, s.kind)
        assertEnvelope(s)
    }

    @Test
    fun `a sealed envelope whose key is gone is reported, never replaced`() {
        store().saveVaultEnvelope(salt, kdf, wrapped)
        val s = store(keyExists = false)
        assertTrue(s.isUnavailable)
        assertFalse(s.hasVault())
        assertNull(s.getVaultEnvelope())
    }

    @Test
    fun `an encrypted store that will not open is still reported as unavailable`() {
        writeLegacy(legacyEncrypted)
        marker.edit().putString("store_kind", VaultMetaStore.KIND_ENCRYPTED).commit()
        assertTrue(store(espOpens = false).isUnavailable)
    }

    @Test
    fun `a database with no readable envelope decides nothing yet`() {
        VaultDatabase.getDatabaseFile(context).apply { parentFile?.mkdirs(); writeText("x") }
        val s = store(espOpens = false)
        assertTrue(s.isUnavailable)
        assertNull(s.kind)
    }

    @Test
    fun `re-wrapping after migration writes the sealed copy`() {
        writeLegacy(legacyEncrypted)
        val s = store()
        val rewrapped = ByteArray(60) { 1 }
        s.saveVaultEnvelope(salt, kdf, rewrapped)
        assertArrayEquals(rewrapped, store().getVaultEnvelope()!!.wrappedDek)
        assertFalse(legacyEncrypted.contains("wrapped_dek_b64"))
    }
}
