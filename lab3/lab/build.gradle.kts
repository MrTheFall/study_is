import org.gradle.api.tasks.Exec

plugins {
    id("org.springframework.boot") version "3.5.6" apply false
}

val pgContainer = providers.environmentVariable("PG_CONTAINER").orElse("orgmgr-pg")
val pgDb = providers.environmentVariable("PG_DB").orElse("studs")
val pgUser = providers.environmentVariable("PG_USER").orElse("postgres")
val pgPassword = providers.environmentVariable("PG_PASSWORD").orElse("postgres")
val pgPort = providers.environmentVariable("PG_PORT").orElse("5432")
val webPort = providers.environmentVariable("WEB_PORT").orElse("13131")
val pgHost = providers.environmentVariable("PG_HOST").orElse("localhost")

extensions.extraProperties.apply {
    set("pgContainer", pgContainer.get())
    set("pgDb", pgDb.get())
    set("pgUser", pgUser.get())
    set("pgPassword", pgPassword.get())
    set("pgPort", pgPort.get())
    set("webPort", webPort.get())
    set("pgHost", pgHost.get())
}

tasks.register("checkstyle") {
    group = "verification"
    description = "Run Checkstyle on main and test sources"
    dependsOn(
        subprojects.flatMap { project ->
            listOf(
                project.tasks.named("checkstyleMain"),
                project.tasks.named("checkstyleTest"),
            )
        },
    )
}

tasks.register<Exec>("startDbDocker") {
    group = "dev"
    description = "Start local Postgres in Docker (if not running)"
    environment("PG_CONTAINER", pgContainer.get())
    environment("PG_DB", pgDb.get())
    environment("PG_USER", pgUser.get())
    environment("PG_PASSWORD", pgPassword.get())
    environment("PG_PORT", pgPort.get())
    commandLine(project.file("scripts/start-db-docker.sh"))
}

tasks.register<Exec>("deploy") {
    group = "deployment"
    description = "Build bootJar and upload it to remote server via scp"
    dependsOn(project(":app").tasks.named("bootJar"))
    environment("DEPLOY_USER", providers.environmentVariable("DEPLOY_USER").orElse("root").get())
    environment("DEPLOY_HOST", providers.environmentVariable("DEPLOY_HOST").orElse("helios.cs.ifmo.ru").get())
    environment("DEPLOY_PORT", providers.environmentVariable("DEPLOY_PORT").orElse("2222").get())
    commandLine(project.file("scripts/deploy.sh"), "--skip-build")
}
