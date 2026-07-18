package conventions

plugins {
    kotlin("jvm")
    id("conventions.abi-validation")
}

kotlin {
    explicitApi()
}
