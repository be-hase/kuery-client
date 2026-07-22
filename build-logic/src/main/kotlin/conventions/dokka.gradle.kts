package conventions

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        // Implementation details live in `internal` packages (annotated with
        // @KueryClientInternalApi); they are public only for cross-module access.
        perPackageOption {
            matchingRegex.set(""".*\.internal(\..*)?""")
            suppress.set(true)
        }
        sourceLink {
            localDirectory.set(project.projectDir)
            remoteUrl("https://github.com/be-hase/kuery-client/tree/main/${project.name}")
            remoteLineSuffix.set("#L")
        }
    }
}
