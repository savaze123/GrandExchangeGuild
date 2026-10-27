import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.osrs.careerguild"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.osrs.careerguild"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Keep APK lean for Google Pixel 10 Pro Fold (Tensor G5 - arm64-v8a)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // GEMINI_API_KEY: add to local.properties (never commit the key)
    //   GEMINI_API_KEY=your_key_here
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            f.inputStream().use { load(it) }
        }
    }
    fun getLocalOrProjectProp(key: String): String {
        return localProps.getProperty(key) ?: (project.findProperty(key) as? String) ?: ""
    }

    defaultConfig {
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${getLocalOrProjectProp("GEMINI_API_KEY")}\"",
        )
        buildConfigField(
            "String",
            "GITHUB_TOKEN",
            "\"${getLocalOrProjectProp("GITHUB_TOKEN")}\"",
        )
        buildConfigField(
            "String",
            "ADZUNA_APP_ID",
            "\"${getLocalOrProjectProp("ADZUNA_APP_ID")}\"",
        )
        buildConfigField(
            "String",
            "ADZUNA_APP_KEY",
            "\"${getLocalOrProjectProp("ADZUNA_APP_KEY")}\"",
        )
        buildConfigField(
            "String",
            "RAPIDAPI_KEY",
            "\"${getLocalOrProjectProp("RAPIDAPI_KEY")}\"",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.google.material)
    implementation(libs.androidx.graphics.path)

    // Pixel 10 Pro Fold: Material3 Adaptive Dual-Screen (ListDetailPaneScaffold)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // 3D Engine: Google Filament (120Hz OSRS Town Hub)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)

    // Data Storage & Local Vault (Room SQLite + SQLCipher + DataStore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore.preferences)

    // Tech Radar Scraper (Ktor Client + Coroutines + Serialization)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // AI & Perception (Google ML Kit Offline Text Recognition v2)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)

    // TRAJECTORY_SCORER (Gemini 1.5 Flash via Google AI SDK)
    implementation(libs.generativeai)

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
