plugins {
    kotlin("jvm") version "2.2.0"
    `maven-publish`
}

group = "edu.brunteless"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jline:jline:3.30.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "kTUI"
            version = project.version.toString()

            pom {
                name.set("kTUI")
                description.set("A Kotlin TUI library")
                url.set("https://github.com/brunteless/kTUI")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/brunteless/kTUI")
                    connection.set(
                        "scm:git:https://github.com/brunteless/kTUI.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://git@github.com:brunteless/kTUI.git"
                    )
                }
                developers {
                    developer {
                        id.set("brunteless")
                        name.set("Bruno Skriniar")
                    }
                }
            }
        }
    }
}