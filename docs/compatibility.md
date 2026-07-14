---
description: API stability policy, Kotlin version policy, and the version compatibility matrix between Kuery Client releases and Kotlin / Spring Boot / Spring Data versions.
---

# Compatibility

## API stability policy

Since version 1.0.0, Kuery Client follows [Semantic Versioning](https://semver.org/).
Backward-incompatible changes to the public API are only introduced in major releases.

The public API surface is verified mechanically:
Kotlin's [explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode) and the
Kotlin Gradle plugin's [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) run in
CI, so unintended API changes are caught before release.

The following are **not** covered by the compatibility guarantee:

- Declarations annotated with `@KueryClientInternalApi`. These are internal APIs that are technically public only
  because they are shared across Kuery Client modules.
- `kuery-client-compiler` internals. The compiler plugin depends on Kotlin compiler internal APIs and its
  implementation may change at any time.

Declarations annotated with `@DelicateKueryClientApi` (such as `addUnsafe` and `bind`) are covered by the guarantee,
but require care in use as documented.

## Kotlin version policy

`kuery-client-compiler` (applied automatically via the Gradle plugin) depends on the Kotlin compiler's internal IR
APIs, which can change between Kotlin releases. Therefore, please align the Kotlin version of your project with the
Kotlin version listed in the table below for the kuery-client version you use.

When a new Kotlin version is released, we follow up with a kuery-client release built against it (as a minor or patch
release, as long as there are no other breaking changes).

## Java version

Kuery Client is built with a Java 17 toolchain, so Java 17 or later is required at runtime.

## Compatibility matrix

The following table lists the versions of Kotlin, Spring Boot, and Spring Data that each kuery-client release
was built against. Aligning your project's dependencies with the versions listed below is recommended for the
best compatibility.

| kuery-client | Kotlin | Spring Boot | Spring Data |
|--------------|--------|-------------|-------------|
| 0.17.0       | 2.4.0  | 4.1.0       | 4.1.0       |
| 0.16.0       | 2.4.0  | 4.0.6       | 4.0.5       |
| 0.15.0       | 2.3.21 | 4.0.6       | 4.0.5       |
| 0.14.0       | 2.3.21 | 4.0.6       | 4.0.5       |
| 0.13.0       | 2.3.10 | 4.0.3       | 4.0.3       |
| 0.12.0       | 2.3.10 | 3.5.10      | 3.5.9       |
| 0.11.0       | 2.2.20 | 3.5.6       | 3.5.4       |
| 0.10.0       | 2.2.0  | 3.5.3       | 3.5.1       |
| 0.9.1        | 2.1.21 | 3.5.3       | 3.5.1       |
| 0.9.0        | 2.1.21 | 3.5.0       | 3.5.0       |
| 0.8.0        | 2.1.21 | 3.4.5       | 3.4.5       |
| 0.7.2        | 2.0.21 | 3.4.4       | 3.4.4       |
| 0.7.1        | 2.0.21 | 3.4.2       | 3.4.2       |
| 0.7.0        | 2.0.21 | 3.4.1       | 3.4.1       |
| 0.6.1        | 2.0.21 | 3.4.1       | 3.4.1       |
| 0.6.0        | 2.0.21 | 3.3.6       | 3.4.1       |
| 0.4.1        | 1.9.24 | 3.3.1       | 3.3.1       |
| 0.4.0        | 1.9.24 | 3.3.1       | 3.3.1       |
| 0.3.0        | 1.9.24 | 3.3.0       | 3.3.1       |
| 0.2.1        | 1.9.24 | 3.3.0       | 3.3.1       |
| 0.2.0        | 1.9.24 | 3.3.0       | 3.3.1       |
| 0.1.1        | 1.9.24 | 3.3.0       | 3.3.1       |
| 0.1.0        | 1.9.24 | 3.3.0       | 3.3.1       |
| 0.0.1        | 1.9.24 | 3.2.6       | 3.2.6       |
