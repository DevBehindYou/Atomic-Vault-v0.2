package com.atomicvault.android.model

import com.squareup.moshi.JsonClass

enum class VaultItemType {
    LOGIN,
    PAYMENT_CARD,
    IDENTITY,
    SECURE_NOTE
}

@JsonClass(generateAdapter = true)
data class CustomField(
    val id: String,
    val label: String,
    val value: String,
    val isSensitive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null
)

@JsonClass(generateAdapter = true)
data class Tag(
    val id: String,
    val name: String,
    val color: String? = null
)

@JsonClass(generateAdapter = true)
data class CredentialItem(
    val id: String,
    val folderId: String? = null,
    val title: String,
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val uriMatchPattern: String? = null,
    val androidPackageName: String? = null,
    val totpSecret: String = "",
    val customFields: List<CustomField> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tags: List<Tag> = emptyList()
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
    val customFields: List<CustomField> = emptyList(),
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tagIds: List<String> = emptyList()
)

data class CredentialPreview(
    val id: String,
    val folderId: String? = null,
    val title: String,
    val username: String = "",
    val uriMatchPattern: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val itemType: VaultItemType = VaultItemType.LOGIN,
    val tags: List<Tag> = emptyList()
)

enum class AutoLockTimeout(val seconds: Int) {
    IMMEDIATELY(0),
    ONE_MINUTE(60),
    FIVE_MINUTES(300),
    FIFTEEN_MINUTES(900),
    NEVER(-1)
}

@JsonClass(generateAdapter = true)
data class VaultSettings(
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES,
    val autoLockSeconds: Int = 300,
    val biometricEnabled: Boolean = false,
    val theme: String = "dark"
)

@JsonClass(generateAdapter = true)
data class VaultEnvelope(
    val saltBase64: String,
    val wrappedDek: String,
    val algorithm: String = "PBKDF2",
    val iterations: Int = 100000,
    val hash: String = "SHA-256"
)

@JsonClass(generateAdapter = true)
data class VaultData(
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val items: List<CredentialItem> = emptyList(),
    val settings: VaultSettings = VaultSettings()
)

@JsonClass(generateAdapter = true)
data class VaultExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val items: List<CredentialItem> = emptyList(),
    val settings: VaultSettings = VaultSettings()
)

enum class VaultStatus {
    LOADING,
    ONBOARDING,
    LOCKED,
    UNLOCKED
}

enum class TrustEventType {
    VAULT_CREATED,
    VAULT_UNLOCKED,
    VAULT_UNLOCK_FAILED,
    VAULT_LOCKED,
    CREDENTIAL_FILLED,
    CREDENTIAL_COPIED,
    CREDENTIAL_CREATED,
    CREDENTIAL_MODIFIED,
    CREDENTIAL_DELETED,
    PASSWORD_GENERATED,
    BIOMETRIC_ENABLED,
    BIOMETRIC_DISABLED,
    BIOMETRIC_AUTH_FAILED,
    BACKUP_EXPORTED,
    BACKUP_IMPORTED,
    SECURITY_SETTING_CHANGED,
    INTEGRITY_CHECK_COMPLETED
}

@JsonClass(generateAdapter = true)
data class TrustEvent(
    val id: String,
    val timestamp: Long,
    val eventType: TrustEventType,
    val subjectReference: String? = null,
    val source: String = "android_app",
    val authType: String? = null,
    val prevHash: String = "",
    val hash: String = ""
)

@JsonClass(generateAdapter = true)
data class TrustLedgerEntry(
    val id: String,
    val timestamp: Long,
    val eventType: TrustEventType,
    val subjectReferenceHash: String? = null,
    val subjectReference: String? = null,
    val targetPackageHash: String? = null,
    val authenticationType: String? = null,
    val source: String = "android_app",
    val result: String = "success",
    val previousHash: String,
    val eventHash: String
)

enum class AuditIssueType {
    REUSED_PASSWORD,
    WEAK_PASSWORD,
    MISSING_PASSWORD
}

data class SecurityAuditFinding(
    val itemId: String,
    val itemTitle: String,
    val issueType: AuditIssueType,
    val recommendation: String,
    val severity: String = "HIGH"
)

data class SecurityAuditReport(
    val score: Int,
    val totalLogins: Int,
    val reusedCount: Int,
    val weakCount: Int,
    val missingCount: Int,
    val findings: List<SecurityAuditFinding>
)

enum class PasswordIssue {
    EMPTY,
    REUSED,
    WEAK
}

data class CredentialFinding(
    val credential: CredentialItem,
    val issues: List<PasswordIssue>,
    val entropy: Double
)

data class VaultSecurityReport(
    val score: Int,
    val reusedCount: Int,
    val weakCount: Int,
    val emptyCount: Int,
    val totalCount: Int,
    val findings: List<CredentialFinding>
)

data class GeneratorOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false
)

enum class Strength {
    WEAK,
    FAIR,
    STRONG,
    EXCELLENT
}

enum class SortOption {
    MODIFIED,
    ALPHABETICAL,
    TYPE
}
