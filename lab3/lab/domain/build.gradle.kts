plugins {
    id("org-manager.common-conventions")
    `java-library`
}

dependencies {
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.orm:hibernate-core")
}
