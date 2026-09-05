package com.example.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupFile {
    fun createShareIntent(context: Context, backupBytes: ByteArray): Intent {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "AtomicVault_backup_$dateStr.vault"
        val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(cacheDir, fileName)
        file.writeBytes(backupBytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AtomicVault Encrypted Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read backup file from selection")
    }
}
