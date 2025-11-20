plugins {
    id("org-manager.common-conventions")
    `java-library`
}

dependencies {
    api(project(":services"))
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-thymeleaf")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
}
