plugins {
    id("openmaps.compose")
    alias(libs.plugins.kotlin.serialization)
}

compose.resources {
    packageOfResClass = "nz.eloque.openmaps.ui.resources"
}

kotlin {
    android {
        androidResources {
            enable = true
        }
    }

    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.activity.compose)
    }

    sourceSets.commonMain.dependencies {
        implementation(project(":core:model"))
        implementation(project(":map"))
        implementation(project(":core:platform"))
        implementation(libs.compose.kit)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.htmlconverter)
        implementation(libs.compose.components.resources)
        implementation(libs.koin.compose)
        implementation(libs.koin.compose.viewmodel)
        implementation(libs.nav3.ui)
        implementation(libs.nav3.viewmodel)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.ktor3)
    }
}
