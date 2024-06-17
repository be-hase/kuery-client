# Detekt Custom Rules

If you use dynamic values without bind, there is a possibility of causing SQL Injection. To prevent this, we provide
Detekt custom ruled.

## How to use

```kotlin
detekt {
    // ...
    dependencies {
        detektPlugins("dev.hsbrysk.kuery-client:kuery-client-detekt:{{version}}")
    }
    // ...
}
```

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
