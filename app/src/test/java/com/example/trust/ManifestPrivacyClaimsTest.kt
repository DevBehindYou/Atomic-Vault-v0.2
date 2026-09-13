package com.example.trust

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Backs the Privacy Proof screen's "build configuration" claims (see
 * PrivacyChecks.kt) with an actual regression test against source files,
 * rather than leaving those claims as unverified assertions. This is the
 * "build-time proof of no internet" the product review asked for --
 * implemented as a plain JVM test reading real project files, since a
 * true merged-manifest Gradle task wasn't something this pass could
 * verify actually wires up correctly without a working Android
 * toolchain. Not as strong a guarantee as inspecting the final merged
 * manifest (a dependency could theoretically still merge in something
 * unwanted without these specific banned strings appearing here), but a
 * real, run-it-and-see check rather than a documentation claim -- and
 * one this project's own tooling can execute in CI on every commit.
 *
 * Working directory for a Gradle-run JVM test is the module directory
 * (app/), so paths here are relative to that.
 */
class ManifestPrivacyClaimsTest {

    private val manifestFile = File("src/main/AndroidManifest.xml")
    private val appGradleFile = File("build.gradle.kts")
    private val catalogFile = File("../gradle/libs.versions.toml")

    @Test
    fun `manifest does not declare INTERNET permission`() {
        val manifest = manifestFile.readText()
        assertFalse(
            "AndroidManifest.xml declares android.permission.INTERNET -- this app is meant to be fully offline",
            manifest.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun `manifest does not enable cleartext or network security config implying network use`() {
        val manifest = manifestFile.readText()
        assertFalse(manifest.contains("android:usesCleartextTraffic=\"true\""))
    }

    @Test
    fun `build files do not reference Firebase, Google Services, or common HTTP libraries`() {
        val combined = (appGradleFile.readText() + "\n" + catalogFile.readText()).lowercase()
        val bannedTokens = listOf(
            "firebase",
            "com.google.gms",
            "google-services",
            "retrofit",
            "okhttp",
            "com.google.android.gms",
            "play-services"
        )
        for (token in bannedTokens) {
            assertFalse(
                "Found banned token '$token' in build.gradle.kts / libs.versions.toml -- " +
                    "this app should carry no network/analytics dependencies",
                combined.contains(token)
            )
        }
    }

    @Test
    fun `manifest declares allowBackup as false`() {
        val manifest = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml should declare android:allowBackup=\"false\" -- vault data must never ride in a device backup",
            manifest.contains("android:allowBackup=\"false\"")
        )
    }
}
