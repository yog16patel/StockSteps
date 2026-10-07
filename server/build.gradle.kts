import java.net.HttpURLConnection
import java.net.URI
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
    // Runs from the installed copy (build/install), not the live build output, so rebuilding or
    // running tests while the mock server is up cannot swap its classes out from under it.
    dependsOn("installDist")
    classpath = fileTree(layout.buildDirectory.dir("install/server/lib"))
    mainClass.set("org.example.stocksteps.ApplicationKt")
    environment("STOCKSTEPS_DATA_MODE", "mock")
    environment("PORT", providers.gradleProperty("port").orElse("8081").get())
}

// Converts raw provider JSON into MOCK fixtures through the backend's own mappers.
// `./gradlew :server:importFixture -Pkind=fmp-quote -Pinput=response.json` (absolute or relative to where you run it)
tasks.register<JavaExec>("importFixture") {
    group = "application"
    description = "Imports a raw provider response as a MOCK-mode fixture."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.example.stocksteps.tools.FixtureImporterKt")
    args(
        providers.gradleProperty("kind").orElse("").get(),
        // Relative paths resolve against the directory ./gradlew was run from, not server/.
        providers.gradleProperty("input").orElse("").get().let { input ->
            if (input.isEmpty()) input else gradle.startParameter.currentDir.resolve(input).absolutePath
        },
        layout.projectDirectory.dir("src/main/resources/fixtures").asFile.absolutePath
    )
}

// Stops a MOCK server started with runMock (even one running in the background).
// `./gradlew :server:stopMock [-Pport=8081]`. Only stops a process whose /api/v1/meta
// reports dataMode "mock", so the real backend is never touched. Uses `lsof` (macOS/Linux).
tasks.register("stopMock") {
    group = "application"
    description = "Stops the MOCK backend listening on the given port (default 8081)."
    val port = providers.gradleProperty("port").orElse("8081")
    doLast {
        val portValue = port.get()
        val meta = runCatching {
            (URI("http://localhost:$portValue/api/v1/meta").toURL().openConnection() as HttpURLConnection).run {
                connectTimeout = 2_000
                readTimeout = 2_000
                inputStream.bufferedReader().use { it.readText() }
            }
        }.getOrNull()
        if (meta == null) {
            println("No StockSteps server is answering on port $portValue.")
            return@doLast
        }
        if (!Regex("\"dataMode\"\\s*:\\s*\"mock\"").containsMatchIn(meta)) {
            throw GradleException("The server on port $portValue is not in mock mode; refusing to stop it.")
        }
        val pids = ProcessBuilder("lsof", "-ti", "tcp:$portValue", "-sTCP:LISTEN").start()
            .inputStream.bufferedReader().readLines().mapNotNull { it.trim().toLongOrNull() }
        pids.forEach { pid -> ProcessHandle.of(pid).ifPresent { it.destroy() } }
        println("Stopped mock server on port $portValue (pid ${pids.joinToString()}).")
    }
}
