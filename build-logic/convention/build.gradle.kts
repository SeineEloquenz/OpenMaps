plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "openmaps.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("compose") {
            id = "openmaps.compose"
            implementationClass = "ComposeConventionPlugin"
        }
    }
}
