plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.habittags.app"
    compileSdk = 35

    defaultConfig {
        // Final after the first Play upload; the Kotlin namespace stays com.habittags.app.
        applicationId = "app.mise"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://bfwyytbghnfwnikpcugx.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_eKASnJMTC64q14v01LMnEQ_IgEQVup8\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A fixed debug key so each CI build installs over the previous one and keeps the user's tags.
    // Debug-only and deliberately public; release builds will use a private key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { animationsDisabled = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.lifecycle.process)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
