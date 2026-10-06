package com.example.backup

import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.VaultItemType

/**
 * Logins from another password manager's CSV export: Google Password
 * Manager / Chrome, Bitwarden, 1Password, KeePass / KeePassXC, Firefox, or
 * any file with recognisable column names. Pure parsing; the caller saves.
 */
object CsvImport {

    data class Result(
        val items: List<CredentialInput>,
        /** Rows that were not logins (cards, notes) or had nothing to fill. */
        val skippedRows: Int
    )

    class NotRecognised(message: String) : IllegalArgumentException(message)

    private val TITLE = listOf("name", "title", "account", "item name")
    private val URL = listOf("url", "login_uri", "website", "web site", "uri", "login url", "hostname")
    private val USERNAME = listOf("username", "login_username", "user name", "login", "email", "user", "login name")
    private val PASSWORD = listOf("password", "login_password", "pass")
    private val NOTES = listOf("note", "notes", "comments", "extra")
    private val TOTP = listOf("totp", "login_totp", "otpauth", "otp", "one-time password", "2fa")
    private val TYPE = listOf("type")

    fun parse(text: String): Result {
        val rows = parseRows(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw NotRecognised("The file is empty.")
        val header = rows.first().map { it.trim().lowercase() }
        fun column(names: List<String>): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val cTitle = column(TITLE)
        val cUrl = column(URL)
        val cUser = column(USERNAME)
        val cPass = column(PASSWORD)
        val cNotes = column(NOTES)
        val cTotp = column(TOTP)
        val cType = column(TYPE)
        if (cPass < 0 || (cUser < 0 && cUrl < 0 && cTitle < 0)) {
            throw NotRecognised("No password column found. Export logins as CSV from your password manager and pick that file.")
        }

        val items = mutableListOf<CredentialInput>()
        var skipped = 0
        for (row in rows.drop(1)) {
            fun cell(i: Int) = if (i in row.indices) row[i].trim() else ""
            val type = cell(cType).lowercase()
            if (type.isNotEmpty() && type != "login") { skipped++; continue }
            val password = if (cPass in row.indices) row[cPass] else ""
            val username = cell(cUser)
            if (password.isEmpty() && username.isEmpty()) { skipped++; continue }
            val rawUrl = cell(cUrl)
            val androidPackage = androidPackage(rawUrl)
            val url = rawUrl.takeIf { it.isNotEmpty() && androidPackage == null }
            val title = cell(cTitle).ifEmpty { url?.let(::host) ?: androidPackage ?: username }
            items += CredentialInput(
                title = title,
                username = username,
                password = password,
                notes = cell(cNotes),
                uriMatchPattern = url,
                androidPackageName = androidPackage,
                totpSecret = cell(cTotp),
                itemType = VaultItemType.LOGIN
            )
        }
        return Result(items, skipped)
    }

    /** Leaves out rows that already exist (same username, password and site or title). */
    fun withoutDuplicates(items: List<CredentialInput>, existing: List<CredentialPlain>): List<CredentialInput> {
        val seen = existing.filter { it.itemType == VaultItemType.LOGIN }
            .map { key(it.username, it.password, it.uriMatchPattern, it.androidPackageName, it.title) }
            .toMutableSet()
        return items.filter { seen.add(key(it.username, it.password, it.uriMatchPattern, it.androidPackageName, it.title)) }
    }

    private fun key(username: String, password: String, url: String?, pkg: String?, title: String): String {
        val place = url?.let(::host)?.removePrefix("www.") ?: pkg ?: title.lowercase()
        return listOf(username.lowercase(), password, place).joinToString("\u0000")
    }

    /** Chrome writes app logins as android://<hash>@<package>/ */
    private fun androidPackage(url: String): String? {
        if (!url.startsWith("android://", ignoreCase = true)) return null
        return url.substringAfter("@", "").trimEnd('/').takeIf { it.isNotEmpty() }
    }

    private fun host(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase()
    }

    /** RFC 4180: quoted fields, doubled quotes, commas and line breaks inside quotes, CRLF or LF. */
    internal fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> { row.add(field.toString()); field.clear() }
                    '\r' -> {}
                    '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
