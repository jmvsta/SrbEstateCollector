# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SrbEstateCollector is a Kotlin application that combines a Ktor HTTP server with a Telegram Bot (kotlin-telegram-bot) for collecting real estate data in Serbia.

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Run tests (JUnit Platform)
./gradlew test

# Run a single test class
./gradlew test --tests "com.jmvstv_v.SomeTestClass"

# Clean build
./gradlew clean build
```

## Tech Stack & Key Dependencies

- **Kotlin 2.2.20** on JVM Toolchain 24, Gradle 8.14
- **Ktor 3.4.0** (Netty engine, port 8080) — HTTP server with ContentNegotiation + kotlinx.serialization JSON
- **kotlin-telegram-bot 6.3.0** (from JitPack) — Telegram Bot API
- **Logback 1.5.32** — logging (config in `src/main/resources/logback.xml`)

## Architecture

Single-module Gradle project. Entry point is `main()` in `src/main/kotlin/Main.kt` (package `com.jmvstv_v`), which starts an embedded Ktor/Netty server.

The `kotlin("plugin.serialization")` Gradle plugin is required for `@Serializable` data classes to work at runtime — without it, kotlinx.serialization cannot find generated serializers.