package com.example.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Password history against the real SQLCipher database and field encryption. */
@RunWith(AndroidJUnit4::class)
class PasswordHistoryDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dek = ByteArray(32) { (it * 3 + 1).toByte() }
    private lateinit var db: SQLiteDatabase
    private lateinit var repo: VaultRepositoryImpl

    @Before
    fun open() {
        VaultDatabase.getDatabaseFile(context).delete()
        db = VaultDatabase.open(context, dek)
        repo = VaultRepositoryImpl(db, dek)
    }

    @After
    fun close() {
        db.close()
        VaultDatabase.getDatabaseFile(context).delete()
    }

    private fun login(password: String) = CredentialInput(title = "Mail", username = "me", password = password)

    @Test
    fun changingThePasswordKeepsTheOldOneNewestFirst() {
        val id = repo.createItem(login("first")).id
        repo.updateItem(id, login("second"))
        repo.updateItem(id, login("third"))
        assertEquals(listOf("second", "first"), repo.passwordHistory(id).map { it.password })
        assertEquals("third", repo.getItem(id)!!.password)
    }

    @Test
    fun savingWithoutAPasswordChangeAddsNothing() {
        val id = repo.createItem(login("same")).id
        repo.updateItem(id, login("same").copy(notes = "edited"))
        assertTrue(repo.passwordHistory(id).isEmpty())
    }

    @Test
    fun onlyTheLatestTenAreKept() {
        val id = repo.createItem(login("p0")).id
        for (i in 1..14) repo.updateItem(id, login("p$i"))
        val history = repo.passwordHistory(id).map { it.password }
        assertEquals(PASSWORD_HISTORY_LIMIT, history.size)
        assertEquals("p13", history.first())
        assertEquals("p4", history.last())
    }

    @Test
    fun deletingTheItemOrClearingRemovesItsHistory() {
        val a = repo.createItem(login("a1")).id
        repo.updateItem(a, login("a2"))
        repo.clearPasswordHistory(a)
        assertTrue(repo.passwordHistory(a).isEmpty())

        val b = repo.createItem(login("b1")).id
        repo.updateItem(b, login("b2"))
        repo.deleteItem(b)
        val left = db.rawQuery("SELECT COUNT(*) FROM password_history;", null as Array<String>?).use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals(0, left)
    }

    @Test
    fun historyIsStoredEncrypted() {
        val id = repo.createItem(login("plain-old-secret")).id
        repo.updateItem(id, login("new"))
        val blob = db.rawQuery("SELECT encrypted_password FROM password_history;", null as Array<String>?).use { c -> c.moveToFirst(); c.getBlob(0) }
        assertTrue(!String(blob, Charsets.ISO_8859_1).contains("plain-old-secret"))
    }
}
