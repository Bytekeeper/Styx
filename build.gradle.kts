import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.11"
    distribution
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

group = "sscait"
version = "1.0-SNAPSHOT"

application.mainClassName = "org.styx.MainKt"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.badlogicgames.gdx:gdx:1.9.4")
    implementation("io.jenetics:jenetics:4.2.0")
    implementation("org.locationtech.jts:jts-core:1.16.0")
    implementation("org.apache.logging.log4j:log4j-api:2.11.1")
    implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    implementation("com.github.JasperGeurtz:JBWAPI:develop-SNAPSHOT")
    implementation("com.github.Bytekeeper:ass:master-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.0.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.2.0")
    testImplementation("org.junit.platform:junit-platform-engine:1.2.0")
    testImplementation("org.assertj:assertj-core:3.9.0")
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions.jvmTarget = "1.8"

val jar: Jar by tasks

jar.apply {
    from(configurations.runtimeClasspath.get().resolve().map { if (it.isDirectory) it else zipTree(it) })
    manifest {
        attributes += "Implementation-Title" to "Gradle Jar File Example"
        attributes += "Implementation-Version" to version
        attributes += "Main-Class" to "org.fttbot.MainKt"
    }
}

distributions {
    getByName("main") {
        contents {
            from("BWAPI4JBridge.dll") {
                into("")
            }
            into("")
        }
    }
}
