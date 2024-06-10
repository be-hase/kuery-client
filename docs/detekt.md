# Detekt Custom Rules (Experimental)

If you use dynamic values without bind, there is a possibility of causing SQL Injection. To prevent this, we provide
Detekt custom ruled.

However, please do not rely on it too much, as there may be cases where complex usage cannot be detected. We plan to
address this issue by using [type resolution](https://detekt.dev/docs/gettingstarted/type-resolution/).

Since this is still experimental, the rules may change.

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
