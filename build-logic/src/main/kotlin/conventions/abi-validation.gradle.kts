package conventions

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("dev.hsbrysk.kuery.core.KueryClientInternalApi")
            }
        }
    }
}
