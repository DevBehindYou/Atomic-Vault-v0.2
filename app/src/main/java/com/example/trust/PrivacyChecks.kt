package com.example.trust

import android.content.Context
import android.content.pm.PackageManager

/**
 * Real, checkable claims for the Privacy Proof screen -- the whole point
 * of "don't trust Atomic, verify Atomic" is that every line here reflects
 * actual system state, not a hardcoded checkmark. Two kinds of check,
 * kept visibly distinct in the UI:
 *
 *  - LIVE: computed against real Android APIs right now, on this device,
 *    at screen-load time (e.g. "is INTERNET actually declared").
 *  - BUILD: facts about how the app was built, not independently
 *    re-verified at runtime here -- covered instead by
 *    ManifestPrivacyClaimsTest, a real (if imperfect) regression test
 *    against the actual manifest/dependency declarations. Labeled
 *    separately rather than presented as if they were live-verified,
 *    since conflating the two is exactly the kind of overclaiming this
 *    feature exists to avoid.
 *
 * Anything not yet actually implemented (screen-capture protection, at
 * the time this was written) is reported as failing/not-yet-active --
 * never faked as passing just because it's on the roadmap.
 */
data class PrivacyCheck(
    val category: String,
    val label: String,
    val passed: Boolean,
    val detail: String,
    val isLiveCheck: Boolean
)

object PrivacyChecks {

    fun runAll(
        context: Context,
        sqlcipherVerified: Boolean,
        biometricArmed: Boolean,
        integrityWarnings: List<String>,
        screenCaptureProtectionActive: Boolean = false // honest default: not yet built, see design plan roadmap
    ): List<PrivacyCheck> {
        val internetDeclared = hasInternetPermission(context)
        val backupAllowed = isBackupAllowed(context)

        return listOf(
            // -- Network Access --
            PrivacyCheck(
                "Network Access", "Internet permission",
                passed = !internetDeclared,
                detail = if (!internetDeclared) "Not declared in the installed app" else "Declared -- unexpected for an offline-only app",
                isLiveCheck = true
            ),
            PrivacyCheck(
                "Network Access", "Cloud / analytics / advertising SDKs",
                passed = true,
                detail = "None declared in the build configuration",
                isLiveCheck = false
            ),

            // -- Vault --
            PrivacyCheck(
                "Vault", "SQLCipher active",
                passed = sqlcipherVerified,
                detail = if (sqlcipherVerified) "Verified against the open vault database" else "Unlock the vault to verify this live",
                isLiveCheck = true
            ),
            PrivacyCheck(
                "Vault", "AES-256-GCM / Argon2id",
                passed = true,
                detail = "Envelope encryption scheme, by design",
                isLiveCheck = false
            ),
            PrivacyCheck(
                "Vault", "Biometric-bound decryption",
                passed = biometricArmed,
                detail = if (biometricArmed) "Active -- DEK release requires a Keystore-bound biometric check" else "Not enabled",
                isLiveCheck = true
            ),

            // -- Android --
            PrivacyCheck(
                "Android", "Automatic cloud backup",
                passed = !backupAllowed,
                detail = if (!backupAllowed) "Disabled" else "Enabled -- vault data could be included in a device backup",
                isLiveCheck = true
            ),
            PrivacyCheck(
                "Android", "Screen capture protection",
                passed = screenCaptureProtectionActive,
                detail = if (screenCaptureProtectionActive) "Active" else "Not yet enabled -- planned hardening work, not shipped yet",
                isLiveCheck = true
            ),
            PrivacyCheck(
                "Android", "Last integrity check",
                passed = integrityWarnings.isEmpty(),
                detail = if (integrityWarnings.isEmpty()) "No warnings" else integrityWarnings.joinToString("; "),
                isLiveCheck = true
            )
        )
    }

    /** (passed count, total count) -- the Trust Score is derived directly from this, never a separate invented number. */
    fun scoreOf(checks: List<PrivacyCheck>): Pair<Int, Int> = checks.count { it.passed } to checks.size

    private fun hasInternetPermission(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.any { it == android.Manifest.permission.INTERNET } ?: false
        } catch (e: Exception) {
            // Fail closed toward "can't verify" being treated as a failed check, not a passed one.
            true
        }
    }

    private fun isBackupAllowed(context: Context): Boolean {
        return try {
            val info = context.packageManager.getApplicationInfo(context.packageName, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        } catch (e: Exception) {
            true
        }
    }
}
