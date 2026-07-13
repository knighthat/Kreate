enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Kreate"
include(":composeApp")
// Projects from extensions
include(":oldtube")
project(":oldtube").projectDir = file("extensions/innertube")
include(":kugou")
project(":kugou").projectDir = file("extensions/kugou")
include(":lrclib")
project(":lrclib").projectDir = file("extensions/lrclib")
include(":discord")
project(":discord").projectDir = file("extensions/discord")
include(":resources")
project(":resources").projectDir = file("extensions/resources")
include(":preferences")
project(":preferences").projectDir = file("extensions/preferences")
include(":widgets")
project(":widgets").projectDir = file("extensions/widgets")
include(":database")
project(":database").projectDir = file("extensions/database")
include(":gateway")
project(":gateway").projectDir = file("extensions/gateway")
