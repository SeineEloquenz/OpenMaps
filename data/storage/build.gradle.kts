plugins {
    id("openmaps.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.room.runtime)
            implementation(libs.multiplatform.settings)
            implementation(libs.koin.core)
            implementation(libs.spatialk.gpx)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.koin.android)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
