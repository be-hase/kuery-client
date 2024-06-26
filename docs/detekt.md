# Detekt Custom Rules

Incorrect usage can result in SQL injection. To detect such cases, we provide custom Detekt rules.

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

### UseStringLiteralRule

In kuery client, string interpolation in string templates passed to `add` and `+`(unaryPlus) is customized. These are
expanded as placeholders.

However, in other places, the default string interpolation is executed.
Therefore, if you write it incorrectly as follows, problems may arise.

#### Noncompliant Code:

```kotlin
client.sql {
    // BAD !!
    val sql = "SELECT * FROM user WHERE id = $id"
    +sql
}
```

#### Compliant Code:

```kotlin
client.sql {
    +"SELECT * FROM user WHERE id = ${bind(id)}"
}
```
