plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val verCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val verName = (project.findProperty("verName") as String?) ?: "1.0.1"

android {
    namespace = "com.example.wsr"
    compileSdk = 37
    ndkVersion = "26.1.10909125"
    defaultConfig {
        applicationId = "com.example.wsr"
        minSdk = 26
        targetSdk = 34
        versionCode = verCode
        versionName = verName
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments("-DWSR_DEPS=${rootDir}/deps") }
        }
    }
    signingConfigs {
        create("app") {
            storeFile = rootProject.file("release.keystore")
            storePassword = "android"
            keyAlias = "wsr"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("app")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.9.2")
}
