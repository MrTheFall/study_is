plugins {
    id("org-manager.common-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":web"))
    implementation("com.alibaba:druid-spring-boot-3-starter:1.2.23")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
