plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.brp.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brp.assistant"
        minSdk = 30
        targetSdk = 35
        versionCode = 24
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // LiteRT-LM .so библиотеки требуют legacy packaging
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ── LLM Engines ─────────────────────────────────────────────────────────────────────

    // Движок 1: MediaPipe LlmInference — поддерживает .task файлы
    implementation(libs.mediapipe.genai)

    // Движок 2: LiteRT-LM — поддерживает .litertlm файлы (Qwen3, Gemma4 и др.)
    // Документация: https://developers.google.com/edge/litert-lm/android
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.1.1")

    // УДАЛЕНО: io.github.aatricks:llmedge:0.4.0beta — legacy-движок, не используется в коде

    // ── Adaptive UI ─────────────────────────────────────────────────────────────────────

    // WindowSizeClass для адаптивной верстки: телефон / планшет / раскладной
    implementation("androidx.compose.material3:material3-window-size-class:1.3.2")

    // ── Coroutines ──────────────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (неконфиденциальные настройки: theme, provider, temperature и т.д.)
    implementation(libs.androidx.datastore.preferences)

    // Security Crypto — стабильная версия вместо alpha06
    // Шифрование API-ключей через EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.0.0")

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Networking (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
