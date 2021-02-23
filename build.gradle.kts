val junitJupiterVersion = "5.7.1"

plugins {
    kotlin("jvm") version "1.4.30"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://kotlin.bintray.com/ktor")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:6d6256d7cc")

    testImplementation("io.mockk:mockk:1.10.0")
    testImplementation("com.github.tomakehurst:wiremock:2.27.1") {
        exclude(group = "junit")
    }
    testImplementation("no.nav:kafka-embedded-env:2.4.0")
    testImplementation("org.awaitility:awaitility:4.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "14"
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.sparkel.sykepengeperioder.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<Wrapper> {
        gradleVersion = "6.8.2"
    }
}
