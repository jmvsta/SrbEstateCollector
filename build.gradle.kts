plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.jmvstv_v"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.jmvstv_v.MainKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    val botApiVer = "6.3.0"
    val ktorVer = "3.4.0"
    val exposedVer = "0.61.0"
    val logVer = "1.5.32"

    // Telegram Bot
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:${botApiVer}")

    // Ktor Server
    implementation("io.ktor:ktor-server-netty:${ktorVer}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVer}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVer}")
    implementation("io.ktor:ktor-server-auth:${ktorVer}")
    implementation("io.ktor:ktor-server-cors:${ktorVer}")

    // Ktor Client (for scrapers)
    implementation("io.ktor:ktor-client-core:${ktorVer}")
    implementation("io.ktor:ktor-client-cio:${ktorVer}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVer}")

    // SQLite + Exposed
    implementation("org.jetbrains.exposed:exposed-core:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVer}")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    // MongoDB
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:5.2.0")

    // Dotenv
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:${logVer}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}