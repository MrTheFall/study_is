import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java`
    id("io.spring.dependency-management")
    id("io.freefair.lombok")
    checkstyle
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

extensions.configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.SHORT
    }
}

extensions.configure<CheckstyleExtension> {
    toolVersion = "10.18.1"
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
}

dependencies {
    add("compileOnly", "com.google.code.findbugs:jsr305:3.0.2")
    add("testCompileOnly", "com.google.code.findbugs:jsr305:3.0.2")
}
