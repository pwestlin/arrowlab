plugins {
    kotlin("jvm") version "1.3.60"
}

group = "nu.westlin.arrowlab"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.10.1")
    implementation(group = "io.arrow-kt", name = "arrow-syntax", version = "0.10.3")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}