plugins {
    id("openmaps.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:model"))
        implementation(project(":map"))
        implementation(libs.compose.kit)
        implementation(libs.compose.material.icons.extended)
    }
}
