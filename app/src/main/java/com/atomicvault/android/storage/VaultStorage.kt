package com.atomicvault.android.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.atomicvault.android.crypto.VaultCrypto
import com.atomicvault.android.model.*
import com.atomicvault.android.trust.TrustLedger
import com.squareup.moshi.Moshi
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object VaultStorage {

    private const val PREFS_NAME = "atomicvault_secure_store"
    private const val KEY_ENVELOPE = "atomicvault_meta_envelope"
    private const val KEY_ENCRYPTED_DATA = "atomicvault_encrypted_data"
    private const val KEY_BIOMETRIC_DEK = "atomicvault_biometric_wrapped_dek"

    private var prefs: SharedPreferences? = null
    private val moshi = Moshi.Builder().build()

    private val envelopeAdapter = moshi.adapter(VaultEnvelope::class.java)
    private val vaultDataAdapter = moshi.adapter(VaultData::class.java)
    private val exportAdapter = moshi.adapter(VaultExportData::class.java)

    var currentDek: SecretKey? = null
        private set
    var currentVaultData: VaultData? = null
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            TrustLedger.init(context)
        }
    }

    fun isVaultInitialized(): Boolean {
        val sp = prefs ?: return false
        return sp.contains(KEY_ENVELOPE)
    }

    fun isBiometricArmed(): Boolean {
        val sp = prefs ?: return false
        return sp.contains(KEY_BIOMETRIC_DEK)
    }

    fun initVault(
        masterPassword: String,
        enableBiometric: Boolean,
        seedSampleItems: Boolean
    ): VaultData {
        val sp = prefs ?: throw IllegalStateException("VaultStorage not initialized")

        val salt = VaultCrypto.generateSalt()
        val kek = VaultCrypto.deriveKek(masterPassword, salt)
        val dek = VaultCrypto.generateDek()
        val wrappedDek = VaultCrypto.wrapDek(kek, dek)

        val envelope = VaultEnvelope(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            wrappedDek = wrappedDek
        )
        sp.edit().putString(KEY_ENVELOPE, envelopeAdapter.toJson(envelope)).apply()

        if (enableBiometric) {
            sp.edit().putString(KEY_BIOMETRIC_DEK, Base64.encodeToString(dek.encoded, Base64.NO_WRAP)).apply()
        } else {
            sp.edit().remove(KEY_BIOMETRIC_DEK).apply()
        }

        val initialData = if (seedSampleItems) {
            createSeedData(enableBiometric)
        } else {
            VaultData(settings = VaultSettings(biometricEnabled = enableBiometric))
        }

        currentDek = dek
        currentVaultData = initialData
        saveVaultInternal(initialData, dek)

        TrustLedger.record(
            eventType = TrustEventType.VAULT_CREATED,
            subjectReference = "master_envelope",
            authType = "password",
            source = "onboarding",
            result = "success"
        )

        return initialData
    }

    fun unlockWithPassword(password: String): VaultData {
        val sp = prefs ?: throw IllegalStateException("VaultStorage not initialized")
        val envJson = sp.getString(KEY_ENVELOPE, null) ?: throw IllegalStateException("No vault envelope found")
        val envelope = envelopeAdapter.fromJson(envJson) ?: throw IllegalStateException("Corrupt vault envelope")

        val salt = Base64.decode(envelope.saltBase64, Base64.NO_WRAP)
        val kek = VaultCrypto.deriveKek(password, salt)

        val dek = try {
            VaultCrypto.unwrapDek(kek, envelope.wrappedDek)
        } catch (e: Exception) {
            TrustLedger.record(
                eventType = TrustEventType.VAULT_UNLOCK_FAILED,
                subjectReference = "master_envelope",
                authType = "password",
                source = "unlock_screen",
                result = "invalid_password"
            )
            throw IllegalArgumentException("Incorrect master password", e)
        }

        val encryptedPayload = sp.getString(KEY_ENCRYPTED_DATA, null)
        val vaultData = if (encryptedPayload.isNullOrEmpty()) {
            VaultData()
        } else {
            try {
                val json = VaultCrypto.open(dek, encryptedPayload)
                vaultDataAdapter.fromJson(json) ?: VaultData()
            } catch (e: Exception) {
                TrustLedger.record(
                    eventType = TrustEventType.VAULT_UNLOCK_FAILED,
                    subjectReference = "vault_payload",
                    authType = "password",
                    source = "unlock_screen",
                    result = "payload_corrupt"
                )
                throw IllegalStateException("Failed to decrypt vault data payload", e)
            }
        }

        currentDek = dek
        currentVaultData = vaultData

        TrustLedger.record(
            eventType = TrustEventType.VAULT_UNLOCKED,
            subjectReference = "master_envelope",
            authType = "password",
            source = "unlock_screen",
            result = "success"
        )

        return vaultData
    }

    fun unlockWithBiometric(): VaultData {
        val sp = prefs ?: throw IllegalStateException("VaultStorage not initialized")
        val rawDekBase64 = sp.getString(KEY_BIOMETRIC_DEK, null)
            ?: throw IllegalStateException("Biometric unlock not armed")

        val rawDek = Base64.decode(rawDekBase64, Base64.NO_WRAP)
        val dek = SecretKeySpec(rawDek, "AES")

        val encryptedPayload = sp.getString(KEY_ENCRYPTED_DATA, null)
        val vaultData = if (encryptedPayload.isNullOrEmpty()) {
            VaultData()
        } else {
            val json = VaultCrypto.open(dek, encryptedPayload)
            vaultDataAdapter.fromJson(json) ?: VaultData()
        }

        currentDek = dek
        currentVaultData = vaultData

        TrustLedger.record(
            eventType = TrustEventType.VAULT_UNLOCKED,
            subjectReference = "biometric_key",
            authType = "biometric",
            source = "unlock_screen",
            result = "success"
        )

        return vaultData
    }

    fun lockVault() {
        currentDek = null
        currentVaultData = null
        TrustLedger.record(
            eventType = TrustEventType.VAULT_LOCKED,
            source = "app_lifecycle",
            result = "success"
        )
    }

    private fun saveVaultInternal(data: VaultData, dek: SecretKey) {
        val sp = prefs ?: throw IllegalStateException("VaultStorage not initialized")
        val json = vaultDataAdapter.toJson(data)
        val encrypted = VaultCrypto.seal(dek, json)
        sp.edit().putString(KEY_ENCRYPTED_DATA, encrypted).apply()
        currentVaultData = data
    }

    fun saveVault(data: VaultData) {
        val dek = currentDek ?: throw IllegalStateException("Vault is locked")
        saveVaultInternal(data, dek)
    }

    fun resetVault() {
        val sp = prefs ?: return
        sp.edit().clear().apply()
        currentDek = null
        currentVaultData = null
        TrustLedger.clear()
    }

    // CRUD Methods
    fun addCredential(input: CredentialInput): CredentialItem {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val allTags = current.tags
        val selectedTags = allTags.filter { it.id in input.tagIds }

        val newItem = CredentialItem(
            id = "cred_${UUID.randomUUID()}",
            folderId = input.folderId,
            title = input.title,
            username = input.username,
            password = input.password,
            notes = input.notes,
            uriMatchPattern = input.uriMatchPattern,
            androidPackageName = input.androidPackageName,
            totpSecret = input.totpSecret,
            customFields = input.customFields,
            updatedAt = System.currentTimeMillis(),
            itemType = input.itemType,
            tags = selectedTags
        )

        val updated = current.copy(items = listOf(newItem) + current.items)
        saveVault(updated)

        TrustLedger.record(
            eventType = TrustEventType.CREDENTIAL_CREATED,
            subjectReference = newItem.title,
            authType = "session_dek",
            source = "credential_editor",
            result = "success"
        )

        return newItem
    }

    fun updateCredential(id: String, input: CredentialInput): CredentialItem {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val allTags = current.tags
        val selectedTags = allTags.filter { it.id in input.tagIds }

        val updatedItem = CredentialItem(
            id = id,
            folderId = input.folderId,
            title = input.title,
            username = input.username,
            password = input.password,
            notes = input.notes,
            uriMatchPattern = input.uriMatchPattern,
            androidPackageName = input.androidPackageName,
            totpSecret = input.totpSecret,
            customFields = input.customFields,
            updatedAt = System.currentTimeMillis(),
            itemType = input.itemType,
            tags = selectedTags
        )

        val updatedItems = current.items.map { if (it.id == id) updatedItem else it }
        saveVault(current.copy(items = updatedItems))

        TrustLedger.record(
            eventType = TrustEventType.CREDENTIAL_MODIFIED,
            subjectReference = updatedItem.title,
            authType = "session_dek",
            source = "credential_editor",
            result = "success"
        )

        return updatedItem
    }

    fun deleteCredential(id: String) {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val item = current.items.find { it.id == id }
        val updatedItems = current.items.filter { it.id != id }
        saveVault(current.copy(items = updatedItems))

        TrustLedger.record(
            eventType = TrustEventType.CREDENTIAL_DELETED,
            subjectReference = item?.title ?: id,
            authType = "session_dek",
            source = "credential_editor",
            result = "success"
        )
    }

    fun addFolder(name: String): Folder {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val newFolder = Folder(id = "folder_${UUID.randomUUID()}", name = name)
        saveVault(current.copy(folders = current.folders + newFolder))
        return newFolder
    }

    fun deleteFolder(id: String) {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val updatedFolders = current.folders.filter { it.id != id }
        val updatedItems = current.items.map { if (it.folderId == id) it.copy(folderId = null) else it }
        saveVault(current.copy(folders = updatedFolders, items = updatedItems))
    }

    fun addTag(name: String, color: String?): Tag {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val newTag = Tag(id = "tag_${UUID.randomUUID()}", name = name, color = color ?: "#10B981")
        saveVault(current.copy(tags = current.tags + newTag))
        return newTag
    }

    fun deleteTag(id: String) {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val updatedTags = current.tags.filter { it.id != id }
        val updatedItems = current.items.map { item ->
            item.copy(tags = item.tags.filter { it.id != id })
        }
        saveVault(current.copy(tags = updatedTags, items = updatedItems))
    }

    fun updateSettings(patch: (VaultSettings) -> VaultSettings) {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val newSettings = patch(current.settings)
        saveVault(current.copy(settings = newSettings))

        val sp = prefs ?: return
        val dek = currentDek ?: return
        if (newSettings.biometricEnabled) {
            sp.edit().putString(KEY_BIOMETRIC_DEK, Base64.encodeToString(dek.encoded, Base64.NO_WRAP)).apply()
            TrustLedger.record(eventType = TrustEventType.BIOMETRIC_ENABLED, source = "settings", result = "success")
        } else {
            sp.edit().remove(KEY_BIOMETRIC_DEK).apply()
            TrustLedger.record(eventType = TrustEventType.BIOMETRIC_DISABLED, source = "settings", result = "success")
        }
    }

    fun exportArchive(passphrase: String): ByteArray {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val exportData = VaultExportData(
            folders = current.folders,
            tags = current.tags,
            items = current.items,
            settings = current.settings
        )
        val json = exportAdapter.toJson(exportData)
        val bytes = VaultCrypto.exportBackup(json, passphrase)

        TrustLedger.record(
            eventType = TrustEventType.BACKUP_EXPORTED,
            source = "backup_screen",
            result = "success"
        )
        return bytes
    }

    fun importArchive(bytes: ByteArray, passphrase: String): Int {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val json = VaultCrypto.importBackup(bytes, passphrase)
        val imported = exportAdapter.fromJson(json) ?: throw IllegalArgumentException("Invalid backup format")

        val existingIds = current.items.map { it.id }.toSet()
        val newItems = imported.items.filter { it.id !in existingIds }

        val mergedFolders = (current.folders + imported.folders).distinctBy { it.id }
        val mergedTags = (current.tags + imported.tags).distinctBy { it.id }
        val mergedItems = current.items + newItems

        saveVault(
            current.copy(
                folders = mergedFolders,
                tags = mergedTags,
                items = mergedItems
            )
        )

        TrustLedger.record(
            eventType = TrustEventType.BACKUP_IMPORTED,
            source = "backup_screen",
            result = "success"
        )
        return newItems.size
    }

    fun exportCsv(): String {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val sb = StringBuilder()
        sb.append("type,name,notes,login_username,login_password,login_uri,login_totp\n")

        for (item in current.items) {
            val typeStr = item.itemType.name.lowercase()
            val name = escapeCsv(item.title)
            val notes = escapeCsv(item.notes)
            val username = escapeCsv(item.username)
            val password = escapeCsv(item.password)
            val uri = escapeCsv(item.uriMatchPattern ?: "")
            val totp = escapeCsv(item.totpSecret)
            sb.append("$typeStr,$name,$notes,$username,$password,$uri,$totp\n")
        }
        return sb.toString()
    }

    fun importCsv(csvText: String): Int {
        val current = currentVaultData ?: throw IllegalStateException("Vault is locked")
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) return 0

        val newItems = mutableListOf<CredentialItem>()
        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.isEmpty()) continue

            val typeStr = cols.getOrNull(0)?.uppercase() ?: "LOGIN"
            val title = cols.getOrNull(1) ?: "Untitled Item"
            val notes = cols.getOrNull(2) ?: ""
            val username = cols.getOrNull(3) ?: ""
            val password = cols.getOrNull(4) ?: ""
            val uri = cols.getOrNull(5)
            val totp = cols.getOrNull(6) ?: ""

            val itemType = try {
                VaultItemType.valueOf(typeStr)
            } catch (e: Exception) {
                VaultItemType.LOGIN
            }

            newItems.add(
                CredentialItem(
                    id = "cred_${UUID.randomUUID()}",
                    title = title,
                    username = username,
                    password = password,
                    notes = notes,
                    uriMatchPattern = uri,
                    totpSecret = totp,
                    itemType = itemType
                )
            )
        }

        saveVault(current.copy(items = current.items + newItems))
        return newItems.size
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    private fun createSeedData(enableBiometric: Boolean): VaultData {
        val folderWork = Folder(id = "f_work", name = "Work")
        val folderPersonal = Folder(id = "f_personal", name = "Personal")

        val tagUrgent = Tag(id = "t_urgent", name = "Urgent", color = "#EF4444")
        val tagFinance = Tag(id = "t_finance", name = "Finance", color = "#10B981")
        val tagWork = Tag(id = "t_work", name = "Work", color = "#3B82F6")

        val seedItems = listOf(
            CredentialItem(
                id = "seed_1",
                folderId = "f_work",
                title = "GitHub Enterprise",
                username = "octocat-dev",
                password = "ghp_X9qK#m2L!v8PzR4wY7tB0e1C",
                notes = "Hardware Security Key backup token registered.",
                uriMatchPattern = "https://github.com",
                androidPackageName = "com.github.android",
                totpSecret = "JBSWY3DPEHPK3PXP",
                tags = listOf(tagWork)
            ),
            CredentialItem(
                id = "seed_2",
                folderId = "f_work",
                title = "Mercury Business Card",
                username = "Alexander Hamilton",
                notes = "Corporate expense card. Monthly limit $25,000.",
                itemType = VaultItemType.PAYMENT_CARD,
                tags = listOf(tagFinance, tagWork),
                customFields = listOf(
                    CustomField(id = "cf_ch", label = "Cardholder Name", value = "Alexander Hamilton"),
                    CustomField(id = "cf_num", label = "Card Number", value = "4242 4242 4242 4242", isSensitive = true),
                    CustomField(id = "cf_exp", label = "Expiration", value = "12/28"),
                    CustomField(id = "cf_cvv", label = "CVV / CVC", value = "882", isSensitive = true),
                    CustomField(id = "cf_pin", label = "ATM PIN", value = "9021", isSensitive = true)
                )
            ),
            CredentialItem(
                id = "seed_3",
                folderId = "f_personal",
                title = "ProtonMail Secure",
                username = "hamilton.alex@proton.me",
                password = "correct-horse-battery-staple-99!",
                notes = "Zero-access encrypted mailbox.",
                uriMatchPattern = "https://mail.proton.me",
                androidPackageName = "ch.protonmail.android",
                tags = listOf(tagUrgent)
            ),
            CredentialItem(
                id = "seed_4",
                folderId = "f_personal",
                title = "Emergency Contact & Passport",
                username = "Alexander Hamilton",
                notes = "Stored securely offline for global travel.",
                itemType = VaultItemType.IDENTITY,
                tags = listOf(tagUrgent),
                customFields = listOf(
                    CustomField(id = "cf_name", label = "Full Legal Name", value = "Alexander Hamilton"),
                    CustomField(id = "cf_doc", label = "Passport / ID Number", value = "USA-992014829", isSensitive = true),
                    CustomField(id = "cf_phone", label = "Phone Number", value = "+1 (555) 019-2834"),
                    CustomField(id = "cf_addr", label = "Home Address", value = "57 Wall Street, New York, NY")
                )
            ),
            CredentialItem(
                id = "seed_5",
                folderId = "f_work",
                title = "AWS Root Console",
                username = "admin@company.com",
                password = "ghp_X9qK#m2L!v8PzR4wY7tB0e1C",
                notes = "Emergency break-glass root credentials. Protected with MFA.",
                uriMatchPattern = "https://signin.aws.amazon.com",
                totpSecret = "K5ZGS2LEMFVWY3C4",
                tags = listOf(tagWork, tagUrgent)
            )
        )

        return VaultData(
            folders = listOf(folderWork, folderPersonal),
            tags = listOf(tagUrgent, tagFinance, tagWork),
            items = seedItems,
            settings = VaultSettings(biometricEnabled = enableBiometric)
        )
    }
}
