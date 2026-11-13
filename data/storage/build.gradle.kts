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
            implementation(libs.koin.core)
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
