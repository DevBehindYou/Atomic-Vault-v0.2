package com.example.backup

import com.example.database.CredentialPlain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImportTest {

    @Test
    fun `Google Password Manager export`() {
        val csv = "name,url,username,password,note\r\n" +
            "github.com,https://github.com/login,alice,\"p,ss\"\"word\",\"line one\nline two\"\r\n" +
            ",android://abc123==@com.example.bank/,bob,secret,\r\n"
        val r = CsvImport.parse(csv)
        assertEquals(2, r.items.size)
        val a = r.items[0]
        assertEquals("github.com", a.title)
        assertEquals("https://github.com/login", a.uriMatchPattern)
        assertEquals("alice", a.username)
        assertEquals("p,ss\"word", a.password)
        assertEquals("line one\nline two", a.notes)
        val b = r.items[1]
        assertEquals("com.example.bank", b.androidPackageName)
        assertNull(b.uriMatchPattern)
        assertEquals("com.example.bank", b.title)
    }

    @Test
    fun `Bitwarden export skips cards and notes`() {
        val csv = "﻿folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            "Work,1,login,Mail,,,0,https://mail.example.com,me@example.com,hunter2,otpauth://totp/x?secret=JBSWY3DPEHPK3PXP\n" +
            ",,note,A note,text,,0,,,,\n" +
            ",,card,Visa,,,0,,,,\n"
        val r = CsvImport.parse(csv)
        assertEquals(1, r.items.size)
        assertEquals(2, r.skippedRows)
        assertEquals("Mail", r.items[0].title)
        assertEquals("hunter2", r.items[0].password)
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", r.items[0].totpSecret)
    }

    @Test
    fun `KeePassXC export`() {
        val csv = "\"Group\",\"Title\",\"Username\",\"Password\",\"URL\",\"Notes\",\"TOTP\",\"Icon\",\"Last Modified\",\"Created\"\n" +
            "\"Root\",\"Bank\",\"u1\",\"pw1\",\"https://bank.example\",\"\",\"\",\"0\",\"\",\"\"\n"
        val r = CsvImport.parse(csv)
        assertEquals("Bank", r.items.single().title)
        assertEquals("pw1", r.items.single().password)
    }

    @Test
    fun `Firefox export takes the title from the site`() {
        val csv = "\"url\",\"username\",\"password\",\"httpRealm\",\"formActionOrigin\",\"guid\"\n" +
            "\"https://www.shop.example:8443/a\",\"x\",\"y\",,\"\",\"{1}\"\n"
        assertEquals("www.shop.example", CsvImport.parse(csv).items.single().title)
    }

    @Test
    fun `rows without username and password are skipped`() {
        val r = CsvImport.parse("name,url,username,password\nEmpty,https://a.example,,\n")
        assertEquals(0, r.items.size)
        assertEquals(1, r.skippedRows)
    }

    @Test(expected = CsvImport.NotRecognised::class)
    fun `a file without a password column is refused`() {
        CsvImport.parse("first,last\nA,B\n")
    }

    @Test(expected = CsvImport.NotRecognised::class)
    fun `an empty file is refused`() {
        CsvImport.parse("\n\n")
    }

    @Test
    fun `logins already in the vault are not added twice`() {
        val parsed = CsvImport.parse(
            "name,url,username,password\n" +
                "GitHub,https://github.com,alice,pw\n" +
                "GitHub copy,https://www.github.com/x,Alice,pw\n" +
                "GitHub,https://github.com,alice,new-pw\n"
        ).items
        val existing = listOf(CredentialPlain(id = "1", title = "gh", username = "alice", password = "pw", uriMatchPattern = "github.com"))
        val fresh = CsvImport.withoutDuplicates(parsed, existing)
        assertEquals(listOf("new-pw"), fresh.map { it.password })
    }
}
