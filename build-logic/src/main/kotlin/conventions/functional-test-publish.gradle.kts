package conventions

plugins {
    `maven-publish`
}

// Build-local Maven repository consumed by kuery-client-gradle-plugin/functional-test, which
// resolves these artifacts through the real published-plugin path (plugin id -> marker artifact
// -> plugin jar -> compiler artifact) instead of classpath injection.
publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = uri(rootProject.layout.buildDirectory.dir("functional-test-repo"))
        }
    }
}
