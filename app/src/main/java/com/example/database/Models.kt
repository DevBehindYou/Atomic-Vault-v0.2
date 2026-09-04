package com.example.database

import com.squareup.moshi.JsonClass

/**
 * Phase 5: payment cards and identity records reuse the existing
 * credential_item + custom_field storage rather than new tables --
 * same encryption pattern, same repository methods, no new platform
 * integration risk. A Payment Card or Identity item simply stores its
 * fields (card number, expiry, full name, address, etc.) as
 * CustomFieldPlain entries instead of using the username/password
 * columns, which stay blank for non-LOGIN types.
 */
enum class VaultItemType {
    LOGIN,
    PAYMENT_CARD,
    IDENTITY,
    SECURE_NOTE
}

@JsonClass(generateAdapter = true)
data class CustomFieldPlain(
    val id: String,
    val label: String,
    val value: String,
    val isSensitive: Boolean
)

@JsonClass(generateAdapter = true)
data class FolderPlain(
    val id: String,
    val name: String,
    val parentId: String? = null
)

@JsonClass(generateAdapter = true)
data class TagPlain(
    val id: String,
    val name: String,
    val color: String? = null
)

@JsonClass(generateAdapter = true)
data class CredentialPlain(
    val id: String,
    val folderId: String? = null,
    val title: String,
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val uriMatchPattern: String? = null,
    val androidPackageName: String? = null,
    val totpSecret: String = "",
    val customFields: List<CustomFieldPlain> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tags: List<TagPlain> = emptyList()
)

data class CredentialInput(
    val folderId: String? = null,
    val title: String,
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val uriMatchPattern: String? = null,
    val androidPackageName: String? = null,
    val totpSecret: String = "",
    val customFields: List<CustomFieldPlain> = emptyList(),
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tagIds: List<String> = emptyList()
)

data class CredentialPreview(
    val id: String,
    val folderId: String?,
    val title: String,
    val username: String,
    val uriMatchPattern: String?,
    val updatedAt: Long,
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tags: List<TagPlain> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VaultSettingsPlain(
    val autoLockSeconds: Int = 60,
    val biometricEnabled: Boolean = true
)

data class VaultSettingsPatch(
    val autoLockSeconds: Int? = null,
    val biometricEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class VaultExport(
    val folders: List<FolderPlain>,
    val items: List<CredentialPlain>,
    val settings: VaultSettingsPlain,
    val tags: List<TagPlain> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis(),
    val version: Int = 1
)

