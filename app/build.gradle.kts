import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val envKeystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && File(it).exists() }

android {
    namespace = "com.tengine.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tengine.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (envKeystorePath != null) {
            create("releaseSigning") {
                storeFile = File(envKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "tengine123"
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "tengine"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: "tengine123"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (envKeystorePath != null) {
                signingConfigs.getByName("releaseSigning")
            } else {
                signingConfigs.getByName("debug")
            }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":tengine-editor"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
