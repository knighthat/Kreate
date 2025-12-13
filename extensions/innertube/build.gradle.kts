
plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation(projects.ktorClientBrotli)

    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.encoding)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.serialization.protobuf)
    implementation(libs.extractor)
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
}
