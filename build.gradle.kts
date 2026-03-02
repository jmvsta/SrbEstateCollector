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

    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:${botApiVer}")
    implementation("io.ktor:ktor-server-netty:${ktorVer}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVer}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVer}")
    implementation("io.ktor:ktor-server-auth:${ktorVer}")
    implementation("io.ktor:ktor-server-cors:${ktorVer}")
    implementation("io.ktor:ktor-client-core:${ktorVer}")
    implementation("io.ktor:ktor-client-cio:${ktorVer}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVer}")
    implementation("org.jetbrains.exposed:exposed-core:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVer}")
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVer}")
    implementation("org.postgresql:postgresql:42.7.4")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("ch.qos.logback:logback-classic:${logVer}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.ktor:ktor-client-mock:${ktorVer}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Dnet.bytebuddy.experimental=true",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
    )
}

tasks.jar {
    manifest { attributes["Main-Class"] = "com.jmvstv_v.MainKt" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(21)
}