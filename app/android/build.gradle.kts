plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "nz.eloque.openmaps"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "nz.eloque.openmaps"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = (property("openmaps.versionCode") as String).toInt()
        versionName = property("openmaps.versionName") as String
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/work/_temp/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs["release"]
        }
        debug {
            applicationIdSuffix = ".dev"
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

    // AGP must not re-strip native libraries such as MapLibre's, or builds stop being reproducible.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":core:platform"))
    implementation(project(":data:transit"))
    implementation(project(":data:geocoding"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.koin.android)
}
