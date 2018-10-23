import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.2.60"
    distribution
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

group = "'scai"
version = "1.0-SNAPSHOT"

application.mainClassName = "org.fttbot.FTTBot"

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compile(fileTree("lib").include("*.jar").exclude("*-sources.jar"))
    compile("com.badlogicgames.gdx:gdx:1.9.4")
    compile("io.jenetics:jenetics:4.2.0")
    compile("org.locationtech.jts:jts-core:1.16.0")

    testCompile("org.junit.jupiter:junit-jupiter-api:5.0.2")
    testCompile("org.junit.platform:junit-platform-launcher:1.2.0")
    testCompile("org.junit.platform:junit-platform-engine:1.2.0")
    testCompile("org.assertj:assertj-core:3.9.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

//distributions {
//    scait {
//        contents {
//            from ('build/libs', 'src', 'lib')
//            exclude('BWAPI4J.jar')
//            into '/'
//        }
//    }
//}
//

tasks.withType<Jar> {
    from(configurations.compile.resolve().map { if (it.isDirectory) it else zipTree(it) })
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

//tasks {
//    "fatjar"(Jar::class) {
//    }
//}


//task fatJar(type: Jar) {
//    manifest {
//        attributes 'Implementation-Title': 'Gradle Jar File Example',
//                'Implementation-Version': version,
//                'Main-Class': 'org.fttbot.MainKt'
//    }
//    baseName = project.name + '-all'
//    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
//    with jar
//}
//tasks.assembleScaitDist.dependsOn(fatJar, sourceJar)
//