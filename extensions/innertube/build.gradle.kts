
plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation( libs.bundles.ktor )
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.extractor)
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
}
