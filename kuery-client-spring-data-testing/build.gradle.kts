plugins {
    id("conventions.preset.base")
    // Deliberately no conventions.public-api / conventions.maven-publish: this module is not
    // published, and its contract classes are excluded from explicit API mode and ABI checks.
}

description = "Shared contract tests for the spring-data modules. Not published."

dependencies {
    // Depends only on the core interfaces, never on the jdbc/r2dbc implementations, so that one
    // module's test classpath is not polluted with the sibling implementation. Each module keeps
    // its ContractDatabase implementation in its own src/test.
    api(projects.kueryClientCore)
    api(libs.kotlin.coroutines.core)
    api(libs.micrometer.observation)

    // Contract tests live in the main source set, so test libraries must be declared explicitly
    // (conventions.kotlin only adds them to testImplementation). implementation is enough even
    // though some of these types appear in contract signatures (e.g. runTest's TestResult):
    // concrete subclasses compile without them, and implementation dependencies still reach the
    // consumers' runtime classpath for JUnit to invoke the inherited tests.
    implementation(platform(libs.junit.bom))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation(libs.assertk)
    implementation(libs.kotlin.coroutines.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
