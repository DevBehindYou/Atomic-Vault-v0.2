package com.example.security

import android.os.Build
import android.os.Debug
import java.io.File

object DeviceIntegrity {
    private val KNOWN_ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    fun checkIntegrity(): List<String> {
        val warnings = mutableListOf<String>()

        // Check for su binaries
        for (path in KNOWN_ROOT_PATHS) {
            if (File(path).exists()) {
                warnings.add("Root binary found at $path")
                break
            }
        }

        // Check test-keys
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            warnings.add("OS build was signed with custom/test release keys")
        }

        // Check debugger connection
        if (Debug.isDebuggerConnected()) {
            warnings.add("Active debugger process attached to application")
        }

        return warnings
    }
}
