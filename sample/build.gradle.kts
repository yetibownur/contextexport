plugins {
    kotlin("jvm") version "2.0.0"
    application
}

application {
    mainClass.set("com.garrettmcbride.contextexport.sample.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
}

kotlin {
    jvmToolchain(21)
}
