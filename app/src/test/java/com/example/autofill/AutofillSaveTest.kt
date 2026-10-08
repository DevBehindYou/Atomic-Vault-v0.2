package com.example.autofill

import com.example.autofill.CredentialMatcher.MatchCandidate
import com.example.autofill.CredentialMatcher.TrustLevel
import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillSaveTest {

    private fun candidate(id: String, level: TrustLevel = TrustLevel.LEVEL_2_DOMAIN) =
        MatchCandidate(id = id, title = "item $id", trustLevel = level)

    private val usernames = mapOf("1" to "a@example.com", "2" to "b@example.com")

    @Test
    fun `two accounts on one site are two items`() {
        val target = AutofillSave.chooseTarget(listOf(candidate("1"), candidate("2")), "b@example.com") { usernames[it] }
        assertEquals("2", target)
    }

    @Test
    fun `a new username creates a new item instead of overwriting another account`() {
        val target = AutofillSave.chooseTarget(listOf(candidate("1"), candidate("2")), "c@example.com") { usernames[it] }
        assertNull(target)
    }

    @Test
    fun `usernames compare ignoring case and surrounding spaces`() {
        val target = AutofillSave.chooseTarget(listOf(candidate("1")), "  A@Example.com ") { usernames[it] }
        assertEquals("1", target)
    }

    @Test
    fun `a weak match is never chosen even with the same username`() {
        val target = AutofillSave.chooseTarget(listOf(candidate("1", TrustLevel.LEVEL_0_HEURISTIC)), "a@example.com") { usernames[it] }
        assertNull(target)
    }

    @Test
    fun `no username updates only when exactly one item could be meant`() {
        assertEquals("1", AutofillSave.chooseTarget(listOf(candidate("1")), "") { usernames[it] })
        assertNull(AutofillSave.chooseTarget(listOf(candidate("1"), candidate("2")), "") { usernames[it] })
        assertNull(AutofillSave.chooseTarget(emptyList(), "") { usernames[it] })
    }

    @Test
    fun `updating a login keeps notes, totp, custom fields, tags, folder and type`() {
        val existing = CredentialPlain(
            id = "1",
            folderId = "work",
            title = "GitHub",
            username = "a@example.com",
            password = "old",
            notes = "recovery codes",
            uriMatchPattern = "github.com",
            totpSecret = "JBSWY3DPEHPK3PXP",
            customFields = listOf(CustomFieldPlain("c1", "API token", "abc", true)),
            itemType = VaultItemType.LOGIN,
            tags = listOf(TagPlain("t1", "2FA"))
        )

        val merged = AutofillSave.mergeInto(existing, "a@example.com", "new", "github.com", "com.android.chrome")

        assertEquals("new", merged.password)
        assertEquals("work", merged.folderId)
        assertEquals("GitHub", merged.title)
        assertEquals("recovery codes", merged.notes)
        assertEquals("JBSWY3DPEHPK3PXP", merged.totpSecret)
        assertEquals(existing.customFields, merged.customFields)
        assertEquals(listOf("t1"), merged.tagIds)
        assertEquals(VaultItemType.LOGIN, merged.itemType)
    }

    @Test
    fun `a blank saved username keeps the stored one`() {
        val existing = CredentialPlain(id = "1", title = "Site", username = "kept@example.com", password = "old")
        assertEquals("kept@example.com", AutofillSave.mergeInto(existing, "", "new", "site.com", null).username)
    }

    @Test
    fun `a web login does not store the browser package`() {
        val item = AutofillSave.newItem("a@example.com", "pw", "github.com", "com.android.chrome")
        assertEquals("github.com", item.uriMatchPattern)
        assertNull(item.androidPackageName)
        assertEquals("github.com", item.title)
    }

    @Test
    fun `a native app login keeps its package`() {
        val item = AutofillSave.newItem("a@example.com", "pw", null, "com.bank.app")
        assertEquals("com.bank.app", item.androidPackageName)
        assertNull(item.uriMatchPattern)
        assertEquals("com.bank.app", item.title)
    }
}
