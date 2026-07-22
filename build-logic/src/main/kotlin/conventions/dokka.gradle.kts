package conventions

import java.net.URI

plugins {
    id("org.jetbrains.dokka")
}

// Both release-time Dokka builds (the Pages deploy and the Maven Central javadoc jar) run on a
// version-tag push, so pin source links to that tag; otherwise `main` moves on after the release
// and published links drift to different lines. Everywhere else (local builds, PR/branch CI,
// workflow_dispatch) GITHUB_REF_NAME is absent or not a version tag, and `main` is the best ref.
val sourceLinkRef: Provider<String> = providers.environmentVariable("GITHUB_REF_NAME")
    .map { if (it.matches(Regex("""v\d+\.\d+\.\d+"""))) it else "main" }
    .orElse("main")

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
            remoteUrl.set(
                sourceLinkRef.map { ref ->
                    URI("https://github.com/be-hase/kuery-client/tree/$ref/${project.name}")
                },
            )
            remoteLineSuffix.set("#L")
        }
    }
}
