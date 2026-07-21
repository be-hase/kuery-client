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
    // The conversion contracts register Spring Data converters (@WritingConverter/@ReadingConverter,
    // Converter/ConverterFactory/GenericConverter), which live in spring-data-commons rather than
    // either implementation module.
    api(libs.spring.data.commons)

    // Contract tests live in the main source set, so test libraries must be declared explicitly
    // (conventions.kotlin only adds them to testImplementation). They are only used inside the
    // contract implementations, so implementation scope suffices; it still reaches the consumers'
    // runtime classpath.
    implementation(platform(libs.junit.bom))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation(libs.assertk)
    implementation(libs.kotlin.coroutines.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
