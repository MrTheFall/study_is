plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.spring.gradle:dependency-management-plugin:1.1.6")
    implementation("io.freefair.gradle:lombok-plugin:9.0.0-rc2")
}
