plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "org.example.stocksteps"
version = "1.0.0"
application {
    mainClass = "org.example.stocksteps.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.finhub.lib)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.clientNegotiation)
    implementation(libs.ktor.serializationJson)
    implementation(libs.ktor.cio)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}