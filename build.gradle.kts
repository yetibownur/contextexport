plugins {
    kotlin("jvm") version "2.0.0"
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "com.garrettmcbride.contextexport"
version = "1.3.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "contextexport"
            version = project.version.toString()
        }
    }
}
