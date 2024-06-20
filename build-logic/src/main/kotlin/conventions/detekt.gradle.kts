package conventions

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom("${rootProject.rootDir}/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    reports {
        sarif.required.set(true)
    }
}
