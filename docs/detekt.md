# Detekt Custom Rules

If you use dynamic values without bind, there is a possibility of causing SQL Injection. To prevent this, we provide
Detekt custom rules.

To ensure safety, it is recommended to use this feature.

## How to use

First, please add it as a dependency in `detektPlugin`.

```kotlin
dependencies {
    detektPlugins("dev.hsbrysk.kuery-client:kuery-client-detekt:{{version}}")
}
```

Next, please add the following to the detekt configuration YAML.
(Unfortunately, custom rules do not work unless they are explicitly enabled.)

```yaml
kuery-client:
  StringInterpolation:
    active: true
  UseStringLiteral:
    active: true
```

After that, by running the detektMain task, you can check for any violations.

```shell
# Please run the detektMain task, as type resolution is being used.
# ref: https://detekt.dev/docs/gettingstarted/type-resolution/
./gradlew detektMain
```

## Rules

### StringInterpolationRule

String interpolation is being performed without using bind.

#### Noncompliant Code:

```kotlin
client.sql {
    +"SELECT * FROM user WHERE id = $id"
}
```

#### Compliant Code:

```kotlin
client.sql {
    +"SELECT * FROM user WHERE id = ${bind(id)}"
}
```

### UseStringLiteralRule

To keep it concise, should use String Literal.

#### Noncompliant Code:

```kotlin
client.sql {
    val sql = "SELECT * FROM user WHERE id = ${bind(id)}"
    +sql
}
```

#### Compliant Code:

```kotlin
client.sql {
    +"SELECT * FROM user WHERE id = ${bind(id)}"
}
```
