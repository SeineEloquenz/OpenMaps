plugins {
    id("openmaps.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:model"))
    }
}
