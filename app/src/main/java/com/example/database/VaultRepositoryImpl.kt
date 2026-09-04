package com.example.database

import android.content.ContentValues
import com.example.crypto.VaultCrypto
import net.sqlcipher.database.SQLiteDatabase
import java.util.UUID

class VaultRepositoryImpl(
    private val db: SQLiteDatabase,
    private val dek: ByteArray
) : VaultRepository {

    private fun genId(): String = UUID.randomUUID().toString().replace("-", "")

    override fun listFolders(): List<FolderPlain> {
        val folders = mutableListOf<FolderPlain>()
        db.rawQuery("SELECT id, name, parent_id FROM folder ORDER BY name COLLATE NOCASE ASC;", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val parentId = if (cursor.isNull(2)) null else cursor.getString(2)
                folders.add(FolderPlain(id, name, parentId))
            }
        }
        return folders
    }

    override fun createFolder(name: String, parentId: String?): FolderPlain {
        val id = genId()
        val cv = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("parent_id", parentId)
        }
        db.insertOrThrow("folder", null, cv)
        return FolderPlain(id, name, parentId)
    }

    override fun renameFolder(id: String, name: String) {
        val cv = ContentValues().apply {
            put("name", name)
        }
        db.update("folder", cv, "id = ?", arrayOf(id))
    }

    override fun deleteFolder(id: String) {
        db.beginTransaction()
        try {
            // Null out folder_id on credentials inside this folder
            val cv = ContentValues().apply {
                putNull("folder_id")
            }
            db.update("credential_item", cv, "folder_id = ?", arrayOf(id))
            db.delete("folder", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun listTags(): List<TagPlain> {
        val tags = mutableListOf<TagPlain>()
        db.rawQuery("SELECT id, name, color FROM tag ORDER BY name COLLATE NOCASE ASC;", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val color = if (cursor.isNull(2)) null else cursor.getString(2)
                tags.add(TagPlain(id, name, color))
            }
        }
        return tags
    }

    override fun createTag(name: String, color: String?): TagPlain {
        val trimmed = name.trim()
        // tag.name has a UNIQUE NOCASE index -- check for an existing
        // match first rather than relying on catching a raw SQL
        // constraint violation, so "Work" and "work" resolve to the
        // same tag instead of either erroring or silently fragmenting.
        db.rawQuery("SELECT id, name, color FROM tag WHERE name = ? COLLATE NOCASE;", arrayOf(trimmed)).use { cursor ->
            if (cursor.moveToFirst()) {
                return TagPlain(cursor.getString(0), cursor.getString(1), if (cursor.isNull(2)) null else cursor.getString(2))
            }
        }
        val id = genId()
        val cv = ContentValues().apply {
            put("id", id)
            put("name", trimmed)
            put("color", color)
            put("created_at", System.currentTimeMillis())
        }
        db.insertOrThrow("tag", null, cv)
        return TagPlain(id, trimmed, color)
    }

    override fun deleteTag(id: String) {
        db.beginTransaction()
        try {
            db.delete("credential_tag", "tag_id = ?", arrayOf(id))
            db.delete("tag", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun getTagsForItem(itemId: String): List<TagPlain> {
        val tags = mutableListOf<TagPlain>()
        db.rawQuery(
            """
            SELECT t.id, t.name, t.color FROM tag t
            INNER JOIN credential_tag ct ON ct.tag_id = t.id
            WHERE ct.item_id = ?
            ORDER BY t.name COLLATE NOCASE ASC;
            """.trimIndent(),
            arrayOf(itemId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val color = if (cursor.isNull(2)) null else cursor.getString(2)
                tags.add(TagPlain(id, name, color))
            }
        }
        return tags
    }

    /** Replace-all pattern, same as custom fields -- delete existing associations, re-insert the current set. */
    private fun setTagsForItem(itemId: String, tagIds: List<String>) {
        db.delete("credential_tag", "item_id = ?", arrayOf(itemId))
        for (tagId in tagIds.distinct()) {
            val cv = ContentValues().apply {
                put("item_id", itemId)
                put("tag_id", tagId)
            }
            db.insert("credential_tag", null, cv)
        }
    }

    override fun listPreviews(folderId: String?, query: String?, tagId: String?): List<CredentialPreview> {
        val sqlBuilder = StringBuilder("SELECT id, folder_id, title, encrypted_username, uri_match_pattern, updated_at, item_type FROM credential_item")
        val args = mutableListOf<String>()
        val whereClauses = mutableListOf<String>()

        if (!folderId.isNullOrBlank()) {
            whereClauses.add("folder_id = ?")
            args.add(folderId)
        }
        if (!tagId.isNullOrBlank()) {
            whereClauses.add("id IN (SELECT item_id FROM credential_tag WHERE tag_id = ?)")
            args.add(tagId)
        }
        if (whereClauses.isNotEmpty()) {
            sqlBuilder.append(" WHERE ").append(whereClauses.joinToString(" AND "))
        }
        sqlBuilder.append(" ORDER BY updated_at DESC;")

        val previews = mutableListOf<CredentialPreview>()
        val trimmedQuery = query?.trim()?.lowercase() ?: ""

        db.rawQuery(sqlBuilder.toString(), args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val fId = if (cursor.isNull(1)) null else cursor.getString(1)
                val title = cursor.getString(2)
                val encUsername = if (cursor.isNull(3)) null else cursor.getBlob(3)
                val uriPattern = if (cursor.isNull(4)) null else cursor.getString(4)
                val updatedAt = cursor.getLong(5)
                val itemType = parseItemType(cursor.getString(6))

                val username = if (encUsername != null && encUsername.isNotEmpty()) {
                    try {
                        VaultCrypto.openField(dek, encUsername)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                if (trimmedQuery.isNotEmpty()) {
                    val matchTitle = title.lowercase().contains(trimmedQuery)
                    val matchUser = username.lowercase().contains(trimmedQuery)
                    val matchUri = uriPattern?.lowercase()?.contains(trimmedQuery) == true
                    if (!matchTitle && !matchUser && !matchUri) {
                        continue
                    }
                }

                previews.add(
                    CredentialPreview(
                        id = id,
                        folderId = fId,
                        title = title,
                        username = username,
                        uriMatchPattern = uriPattern,
                        updatedAt = updatedAt,
                        itemType = itemType,
                        tags = getTagsForItem(id)
                    )
                )
            }
        }
        return previews
    }

    /** Falls back to LOGIN for anything unrecognized -- a forward-compatible read (e.g. after a restore from a newer version) should never crash the list screen. */
    private fun parseItemType(raw: String?): VaultItemType =
        try {
            VaultItemType.valueOf(raw ?: "LOGIN")
        } catch (e: IllegalArgumentException) {
            VaultItemType.LOGIN
        }

    override fun getItem(id: String): CredentialPlain? {
        var credential: CredentialPlain? = null
        db.rawQuery(
            """
            SELECT id, folder_id, title, encrypted_username, encrypted_password, encrypted_notes,
                   encrypted_totp_secret, uri_match_pattern, android_package_name, updated_at, item_type
            FROM credential_item WHERE id = ?;
            """.trimIndent(),
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val folderId = if (cursor.isNull(1)) null else cursor.getString(1)
                val title = cursor.getString(2)
                val encUser = if (cursor.isNull(3)) null else cursor.getBlob(3)
                val encPass = if (cursor.isNull(4)) null else cursor.getBlob(4)
                val encNotes = if (cursor.isNull(5)) null else cursor.getBlob(5)
                val encTotp = if (cursor.isNull(6)) null else cursor.getBlob(6)
                val uriPattern = if (cursor.isNull(7)) null else cursor.getString(7)
                val pkgName = if (cursor.isNull(8)) null else cursor.getString(8)
                val updatedAt = cursor.getLong(9)
                val itemType = parseItemType(cursor.getString(10))

                val username = VaultCrypto.openField(dek, encUser)
                val password = VaultCrypto.openField(dek, encPass)
                val notes = VaultCrypto.openField(dek, encNotes)
                val totpSecret = VaultCrypto.openField(dek, encTotp)

                val customFields = getCustomFieldsForItem(id)
                val tags = getTagsForItem(id)

                credential = CredentialPlain(
                    id = id,
                    folderId = folderId,
                    title = title,
                    username = username,
                    password = password,
                    notes = notes,
                    uriMatchPattern = uriPattern,
                    androidPackageName = pkgName,
                    totpSecret = totpSecret,
                    customFields = customFields,
                    updatedAt = updatedAt,
                    itemType = itemType,
                    tags = tags
                )
            }
        }
        return credential
    }

    private fun getCustomFieldsForItem(itemId: String): List<CustomFieldPlain> {
        val fields = mutableListOf<CustomFieldPlain>()
        db.rawQuery(
            "SELECT id, label, encrypted_value, is_sensitive FROM custom_field WHERE item_id = ?;",
            arrayOf(itemId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fieldId = cursor.getString(0)
                val label = cursor.getString(1)
                val encVal = if (cursor.isNull(2)) null else cursor.getBlob(2)
                val isSensitive = cursor.getInt(3) == 1
                val value = VaultCrypto.openField(dek, encVal)
                fields.add(CustomFieldPlain(fieldId, label, value, isSensitive))
            }
        }
        return fields
    }

    override fun createItem(input: CredentialInput): CredentialPlain {
        val id = genId()
        val now = System.currentTimeMillis()

        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("id", id)
                put("folder_id", input.folderId)
                put("title", input.title)
                if (input.username.isNotEmpty()) {
                    put("encrypted_username", VaultCrypto.sealField(dek, input.username))
                }
                if (input.password.isNotEmpty()) {
                    put("encrypted_password", VaultCrypto.sealField(dek, input.password))
                }
                if (input.notes.isNotEmpty()) {
                    put("encrypted_notes", VaultCrypto.sealField(dek, input.notes))
                }
                if (input.totpSecret.isNotEmpty()) {
                    put("encrypted_totp_secret", VaultCrypto.sealField(dek, input.totpSecret))
                }
                put("uri_match_pattern", input.uriMatchPattern)
                put("android_package_name", input.androidPackageName)
                put("updated_at", now)
                put("item_type", input.itemType.name)
            }
            db.insertOrThrow("credential_item", null, cv)

            val createdCustomFields = mutableListOf<CustomFieldPlain>()
            for (cf in input.customFields) {
                val cfId = if (cf.id.isNotBlank()) cf.id else genId()
                val cfCv = ContentValues().apply {
                    put("id", cfId)
                    put("item_id", id)
                    put("label", cf.label)
                    put("encrypted_value", VaultCrypto.sealField(dek, cf.value))
                    put("is_sensitive", if (cf.isSensitive) 1 else 0)
                }
                db.insertOrThrow("custom_field", null, cfCv)
                createdCustomFields.add(CustomFieldPlain(cfId, cf.label, cf.value, cf.isSensitive))
            }

            setTagsForItem(id, input.tagIds)
            val assignedTags = getTagsForItem(id)

            logAudit(id, "create", now)
            db.setTransactionSuccessful()

            return CredentialPlain(
                id = id,
                folderId = input.folderId,
                title = input.title,
                username = input.username,
                password = input.password,
                notes = input.notes,
                uriMatchPattern = input.uriMatchPattern,
                androidPackageName = input.androidPackageName,
                totpSecret = input.totpSecret,
                customFields = createdCustomFields,
                updatedAt = now,
                itemType = input.itemType,
                tags = assignedTags
            )
        } finally {
            db.endTransaction()
        }
    }

    override fun updateItem(id: String, input: CredentialInput): CredentialPlain {
        val now = System.currentTimeMillis()

        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("folder_id", input.folderId)
                put("title", input.title)
                put("encrypted_username", if (input.username.isNotEmpty()) VaultCrypto.sealField(dek, input.username) else null)
                put("encrypted_password", if (input.password.isNotEmpty()) VaultCrypto.sealField(dek, input.password) else null)
                put("encrypted_notes", if (input.notes.isNotEmpty()) VaultCrypto.sealField(dek, input.notes) else null)
                put("encrypted_totp_secret", if (input.totpSecret.isNotEmpty()) VaultCrypto.sealField(dek, input.totpSecret) else null)
                put("uri_match_pattern", input.uriMatchPattern)
                put("android_package_name", input.androidPackageName)
                put("updated_at", now)
                put("item_type", input.itemType.name)
            }
            db.update("credential_item", cv, "id = ?", arrayOf(id))

            // Replace custom fields
            db.delete("custom_field", "item_id = ?", arrayOf(id))
            val updatedCustomFields = mutableListOf<CustomFieldPlain>()
            for (cf in input.customFields) {
                val cfId = if (cf.id.isNotBlank()) cf.id else genId()
                val cfCv = ContentValues().apply {
                    put("id", cfId)
                    put("item_id", id)
                    put("label", cf.label)
                    put("encrypted_value", VaultCrypto.sealField(dek, cf.value))
                    put("is_sensitive", if (cf.isSensitive) 1 else 0)
                }
                db.insertOrThrow("custom_field", null, cfCv)
                updatedCustomFields.add(CustomFieldPlain(cfId, cf.label, cf.value, cf.isSensitive))
            }

            setTagsForItem(id, input.tagIds)
            val assignedTags = getTagsForItem(id)

            logAudit(id, "update", now)
            db.setTransactionSuccessful()

            return CredentialPlain(
                id = id,
                folderId = input.folderId,
                title = input.title,
                username = input.username,
                password = input.password,
                notes = input.notes,
                uriMatchPattern = input.uriMatchPattern,
                androidPackageName = input.androidPackageName,
                totpSecret = input.totpSecret,
                customFields = updatedCustomFields,
                updatedAt = now,
                itemType = input.itemType,
                tags = assignedTags
            )
        } finally {
            db.endTransaction()
        }
    }

    override fun deleteItem(id: String) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.delete("custom_field", "item_id = ?", arrayOf(id))
            db.delete("credential_tag", "item_id = ?", arrayOf(id))
            db.delete("credential_item", "id = ?", arrayOf(id))
            logAudit(id, "delete", now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun logAudit(itemId: String?, action: String, timestamp: Long) {
        val cv = ContentValues().apply {
            put("id", genId())
            put("item_id", itemId)
            put("action", action)
            put("timestamp", timestamp)
        }
        db.insert("audit_log_entry", null, cv)
    }

    override fun getSettings(): VaultSettingsPlain {
        var settings = VaultSettingsPlain()
        db.rawQuery("SELECT auto_lock_seconds, biometric_enabled FROM vault_settings WHERE id = 1;", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val autoLock = cursor.getInt(0)
                val bio = cursor.getInt(1) == 1
                settings = VaultSettingsPlain(autoLockSeconds = autoLock, biometricEnabled = bio)
            }
        }
        return settings
    }

    override fun updateSettings(patch: VaultSettingsPatch): VaultSettingsPlain {
        val current = getSettings()
        val newAutoLock = patch.autoLockSeconds ?: current.autoLockSeconds
        val newBio = patch.biometricEnabled ?: current.biometricEnabled

        val cv = ContentValues().apply {
            put("auto_lock_seconds", newAutoLock)
            put("biometric_enabled", if (newBio) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.update("vault_settings", cv, "id = 1", null)
        return VaultSettingsPlain(autoLockSeconds = newAutoLock, biometricEnabled = newBio)
    }

    override fun exportData(): VaultExport {
        val folders = listFolders()
        val allItems = mutableListOf<CredentialPlain>()

        val itemIds = mutableListOf<String>()
        db.rawQuery("SELECT id FROM credential_item ORDER BY title COLLATE NOCASE ASC;", null).use { cursor ->
            while (cursor.moveToNext()) {
                itemIds.add(cursor.getString(0))
            }
        }

        for (id in itemIds) {
            getItem(id)?.let { allItems.add(it) }
        }

        val settings = getSettings()
        return VaultExport(
            folders = folders,
            items = allItems,
            settings = settings,
            tags = listTags(),
            exportedAt = System.currentTimeMillis(),
            version = 1
        )
    }

    override fun importReplace(data: VaultExport) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            // Delete all custom fields, tags, then credential items, then folders
            db.delete("custom_field", null, null)
            db.delete("credential_tag", null, null)
            db.delete("tag", null, null)
            db.delete("credential_item", null, null)
            db.delete("folder", null, null)

            // Re-insert folders
            for (f in data.folders) {
                val fCv = ContentValues().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("parent_id", f.parentId)
                }
                db.insert("folder", null, fCv)
            }

            // Re-insert tags (using each backup's original id -- items
            // below reference these same ids via their own embedded
            // tags list, so ids must round-trip exactly for the
            // associations to survive the restore).
            for (t in data.tags) {
                val tCv = ContentValues().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("color", t.color)
                    put("created_at", now)
                }
                db.insert("tag", null, tCv)
            }

            // Re-insert items with current session DEK encryption
            for (item in data.items) {
                val iCv = ContentValues().apply {
                    put("id", item.id)
                    put("folder_id", item.folderId)
                    put("title", item.title)
                    if (item.username.isNotEmpty()) {
                        put("encrypted_username", VaultCrypto.sealField(dek, item.username))
                    }
                    if (item.password.isNotEmpty()) {
                        put("encrypted_password", VaultCrypto.sealField(dek, item.password))
                    }
                    if (item.notes.isNotEmpty()) {
                        put("encrypted_notes", VaultCrypto.sealField(dek, item.notes))
                    }
                    if (item.totpSecret.isNotEmpty()) {
                        put("encrypted_totp_secret", VaultCrypto.sealField(dek, item.totpSecret))
                    }
                    put("uri_match_pattern", item.uriMatchPattern)
                    put("android_package_name", item.androidPackageName)
                    put("updated_at", item.updatedAt)
                    put("item_type", item.itemType.name)
                }
                db.insert("credential_item", null, iCv)

                for (cf in item.customFields) {
                    val cfCv = ContentValues().apply {
                        put("id", cf.id.ifBlank { genId() })
                        put("item_id", item.id)
                        put("label", cf.label)
                        put("encrypted_value", VaultCrypto.sealField(dek, cf.value))
                        put("is_sensitive", if (cf.isSensitive) 1 else 0)
                    }
                    db.insert("custom_field", null, cfCv)
                }

                for (tag in item.tags) {
                    val ctCv = ContentValues().apply {
                        put("item_id", item.id)
                        put("tag_id", tag.id)
                    }
                    db.insert("credential_tag", null, ctCv)
                }
            }

            // Apply settings
            val sCv = ContentValues().apply {
                put("auto_lock_seconds", data.settings.autoLockSeconds)
                put("biometric_enabled", if (data.settings.biometricEnabled) 1 else 0)
                put("updated_at", now)
            }
            db.update("vault_settings", sCv, "id = 1", null)

            logAudit(null, "import", now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
