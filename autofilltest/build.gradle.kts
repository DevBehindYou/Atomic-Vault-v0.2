// A tiny login screen used only by the CI emulator check, so AtomicVault's
// Autofill service can be exercised by real Android without a browser.
// Plain Views, no dependencies; debug builds only.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.atomicvault.autofilltest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atomicvault.autofilltest"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    lint {
        // Test fixture only; the app module's lint is what gates CI.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

androidComponents {
    beforeVariants { variant ->
        if (variant.buildType == "release") variant.enable = false
    }
}
