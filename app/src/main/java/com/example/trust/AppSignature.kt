package com.example.trust

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * The SHA-256 of the certificate this APK is signed with, so a user can
 * compare it with the hash published for the release ("Verify it yourself"
 * on Privacy proof). A modified or repackaged APK has a different one.
 */
object AppSignature {

    fun signingCertSha256(context: Context): String? = try {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return null
        val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        signers?.lastOrNull()?.toByteArray()?.let { format(MessageDigest.getInstance("SHA-256").digest(it)) }
    } catch (e: Exception) {
        null
    }

    /** "AB:CD:…", the way `apksigner verify --print-certs` and `keytool` print it. */
    internal fun format(digest: ByteArray): String = digest.joinToString(":") { "%02X".format(it) }
}
