plugins {
    id("openmaps.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.maplibre.compose)
            implementation(project(":core:model"))
        }
        androidMain.dependencies {
            runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
        }
    }
}
