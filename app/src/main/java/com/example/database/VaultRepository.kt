package com.example.database

interface VaultRepository {
    fun listFolders(): List<FolderPlain>
    fun createFolder(name: String, parentId: String? = null): FolderPlain
    fun renameFolder(id: String, name: String)
    fun deleteFolder(id: String)

    fun listTags(): List<TagPlain>
    fun createTag(name: String, color: String? = null): TagPlain
    fun deleteTag(id: String)

    fun listPreviews(folderId: String? = null, query: String? = null, tagId: String? = null): List<CredentialPreview>
    fun getItem(id: String): CredentialPlain?
    fun createItem(input: CredentialInput): CredentialPlain
    fun updateItem(id: String, input: CredentialInput): CredentialPlain
    fun deleteItem(id: String)

    /** Earlier passwords of a login, newest first (at most [PASSWORD_HISTORY_LIMIT]). */
    fun passwordHistory(itemId: String): List<PasswordHistoryEntry>
    fun clearPasswordHistory(itemId: String)

    fun getSettings(): VaultSettingsPlain
    fun updateSettings(patch: VaultSettingsPatch): VaultSettingsPlain

    fun exportData(): VaultExport
    fun importReplace(data: VaultExport)
}

/** How many earlier passwords are kept per login. */
const val PASSWORD_HISTORY_LIMIT = 10
