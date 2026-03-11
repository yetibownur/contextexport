plugins {
    kotlin("jvm") version "2.0.0"
    `maven-publish`
}

group = "com.garrettmcbride.contextexport"
version = "1.0.0"

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
