import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Kotlin Multiplatform library with the Android target. Namespace is derived from the project path. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            fun version(name: String) = libs.findVersion(name).get().requiredVersion

            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
                targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                    namespace = "nz.eloque.openmaps" + path.replace(':', '.')
                    compileSdk = version("compileSdk").toInt()
                    buildToolsVersion = version("buildTools")
                    minSdk = version("minSdk").toInt()
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                    withHostTest {}
                }
            }
        }
}
