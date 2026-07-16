import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    // Multiplatform
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)

    // Android
    alias( libs.plugins.android.kotlin.multiplatform.library )
    alias( libs.plugins.android.lint )

    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias( libs.plugins.license.report )
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    android {
        namespace = "app.kreate.compose"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Expose resources to other modules.
        // Required! Without this option, final APK won't include this module's resources
        androidResources.enable = true

        compilerOptions {
            enableCoreLibraryDesugaring = true
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }

    jvm()

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        jvmMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.desktop.currentOs)

            implementation(libs.material.icons.desktop.ext)
            implementation(libs.vlcj)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.guava)
            implementation(libs.androidx.webkit)

            implementation( libs.androidx.constraintlayout )
            implementation( libs.androidx.appcompat )
            implementation( libs.androidx.appcompat.resources )
            implementation( libs.androidx.palette )
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.process)

            implementation( libs.monetcompat )
            implementation(libs.androidmaterial)

            // Player implementations
            implementation( libs.media3.exoplayer )
            implementation(libs.media3.session)
            implementation( libs.media3.datasource.okhttp )
            implementation( libs.androidyoutubeplayer )

            implementation( libs.toasty )
            implementation( libs.logpose.android )
        }
        androidUnitTest.dependencies {
            implementation( libs.junit4 )
            implementation( libs.robolectric )
            implementation( libs.androidx.test )
        }
        commonMain.dependencies {
            api( projects.resources )
            api( projects.preferences )
            api( projects.database )
            implementation( projects.widgets )
            implementation( projects.gateway )

            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(projects.oldtube)
            implementation(projects.kugou)
            implementation(projects.lrclib)
            implementation( projects.discord )

            implementation(libs.navigation.kmp)

            //coil3 mp
            implementation( libs.coil3.compose )
            implementation( libs.coil3.network.ktor )

            implementation(libs.translator)

            implementation( libs.bundles.compose.kmp )

            implementation ( libs.hypnoticcanvas )
            implementation ( libs.hypnoticcanvas.shaders )

            implementation( libs.kotlin.csv )

            implementation( libs.bundles.ktor )
            implementation( libs.okhttp3.logging.interceptor )
            implementation( libs.okhttp3.dns.over.https )

            implementation( libs.math3 )

            implementation( libs.material.icons.kmp )

            // Dependency injection
            implementation( libs.koin.core )
            implementation( libs.koin.navigation )

            // Logging
            implementation( libs.kermit )
            implementation( libs.kermit.io )

            implementation( libs.colorpicker )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.desktop {
    application {

        mainClass = "MainKt"

        //conveyor
        version = "0.0.1"
        group = "me.knighthat.kreate"

        //jpackage
        nativeDistributions {
            //conveyor
            vendor = "RiMusic.DesktopApp"
            description = "RiMusic Desktop Music Player"

            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "RiMusic.DesktopApp"
            packageVersion = "0.0.1"
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
}

dependencies {
    coreLibraryDesugaring(libs.desugaring.nio)
}

// Use `gradlew dependencies` to get report in composeApp/build/reports/dependency-license
licenseReport {
    // Select projects to examine for dependencies.
    // Defaults to current project and all its subprojects
    projects = arrayOf( project )

    // Adjust the configurations to fetch dependencies. Default is 'runtimeClasspath'
    // For Android projects use 'releaseRuntimeClasspath' or 'yourFlavorNameReleaseRuntimeClasspath'
    // Use 'ALL' to dynamically resolve all configurations:
    // configurations = ALL
    configurations = arrayOf( "githubUniversalProdUncompressedRuntimeClasspath" )

    // Don't include artifacts of project's own group into the report
    excludeOwnGroup = true

    // Don't exclude bom dependencies.
    // If set to true, then all BOMs will be excluded from the report
    excludeBoms = true

    // Set custom report renderer, implementing ReportRenderer.
    // Yes, you can write your own to support any format necessary.
    renderers = arrayOf( JsonReportRenderer() )

    filters = arrayOf<DependencyFilter>( ExcludeTransitiveDependenciesFilter() )
}
