plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.atomicvault.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atomicvault.android"
        minSdk = 28 // Required minimum Android SDK 9.0 (Pie)
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
                ?: (project.findProperty("ANDROID_KEYSTORE_PATH") as? String)
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                ?: (project.findProperty("ANDROID_KEYSTORE_PASSWORD") as? String)
            val keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                ?: System.getenv("ANDROID_KEYSTORE_ALIAS")
                ?: (project.findProperty("ANDROID_KEY_ALIAS") as? String)
                ?: (project.findProperty("ANDROID_KEYSTORE_ALIAS") as? String)
            val keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                ?: System.getenv("ANDROID_KEYSTORE_PRIVATE_KEY_PASSWORD")
                ?: (project.findProperty("ANDROID_KEY_PASSWORD") as? String)
                ?: (project.findProperty("ANDROID_KEYSTORE_PRIVATE_KEY_PASSWORD") as? String)

            if (!keystorePath.isNullOrBlank()) {
                val keystoreFile = file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                    enableV4Signing = false
                }
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        create("internal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".internal"
            matchingFallbacks += listOf("release")
            // Signed with standard debug keystore strictly for local R8 & minification testing
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Security & Biometric
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.autofill)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.sqlite.ktx)

    // Serialization
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

gradle.taskGraph.whenReady {
    val productionReleaseTasks = setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "packageReleaseBundle"
    )
    val isPackagingProductionRelease = allTasks.any { task ->
        task.name in productionReleaseTasks
    }
    if (isPackagingProductionRelease) {
        val releaseSigning = android.signingConfigs.getByName("release")
        if (releaseSigning.storeFile == null || !releaseSigning.storeFile!!.exists()) {
            throw GradleException(
                "CRITICAL: Production release build requires valid production signing credentials.\n" +
                "Missing or invalid ANDROID_KEYSTORE_PATH.\n" +
                "Production package 'com.atomicvault.android' must NEVER be signed with debug keys or left unsigned.\n" +
                "For local testing of minified release behavior, run './gradlew assembleInternal'."
            )
        }
    }
}

