plugins {
    id("org-manager.common-conventions")
    `java-library`
}

dependencies {
    api(project(":domain"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.ehcache:ehcache")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("software.amazon.awssdk:s3:2.27.18")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
