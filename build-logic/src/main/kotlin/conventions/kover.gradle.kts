package conventions

plugins {
    id("org.jetbrains.kotlinx.kover")
}

// Modules with conventions.jmh have a "jmh" source set alongside "main"; without this, its
// benchmark classes are instrumented as always-uncovered production code.
kover {
    currentProject {
        sources {
            excludedSourceSets.addAll("jmh")
        }
    }
}
