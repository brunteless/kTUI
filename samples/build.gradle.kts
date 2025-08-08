plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("MvpKt")
}

dependencies {
    implementation(project(":lib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.shadowJar {
    archiveBaseName.set("kTUI-MVP")
    archiveClassifier.set("")
}

kotlin {
    jvmToolchain(21)
}