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
    implementation("com.google.cloud:google-cloud-firestore:3.45.0")
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    api(project(":core"))
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback)
    implementation(libs.finhub.lib)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.clientNegotiation)
    implementation(libs.ktor.serializationJson)
    implementation(libs.ktor.cio)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
// MOCK mode: serves captured fixtures, needs no provider keys. `./gradlew :server:runMock [-Pport=8081]`
// Port 8081 matches the apps' default mock backend URL; the real backend stays on 8080.
tasks.register<JavaExec>("runMock") {
    group = "application"
    description = "Runs the StockSteps backend with fixture data (STOCKSTEPS_DATA_MODE=mock)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.example.stocksteps.ApplicationKt")
    environment("STOCKSTEPS_DATA_MODE", "mock")
    environment("PORT", providers.gradleProperty("port").orElse("8081").get())
}

// Converts raw provider JSON into MOCK fixtures through the backend's own mappers.
// `./gradlew :server:importFixture -Pkind=fmp-quote -Pinput=/path/to/response.json`
tasks.register<JavaExec>("importFixture") {
    group = "application"
    description = "Imports a raw provider response as a MOCK-mode fixture."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.example.stocksteps.tools.FixtureImporterKt")
    args(
        providers.gradleProperty("kind").orElse("").get(),
        providers.gradleProperty("input").orElse("").get(),
        layout.projectDirectory.dir("src/main/resources/fixtures").asFile.absolutePath
    )
}
