---
description: Register Spring's @WritingConverter / @ReadingConverter via the builder's converters(...) to support custom types.
---

# Type Conversion

Kuery Client uses [Spring Type Conversion](https://docs.spring.io/spring-framework/reference/core/validation/convert.html)
for values written to bind parameters and values read into mapped properties. Register Spring `@WritingConverter`
and `@ReadingConverter` implementations on the client builder.

## Default behavior

Without any configuration:

- Types the driver supports natively (numbers, strings, date/time types, and so on) are passed through unchanged.
- Enums are written by their name (`Enum.name`) and read back by name.

Custom converters registered via `converters(...)` take precedence over these defaults. For example, registering
a `@WritingConverter` from your enum to `Int` overrides the write-by-name default for that enum.

## Example

### Define a custom type

```kotlin
data class StringWrapper(val value: String)
```

### Create the converters

```kotlin
@WritingConverter
class StringWrapperToStringConverter : Converter<StringWrapper, String> {
    override fun convert(source: StringWrapper): String {
        return source.value
    }
}

@ReadingConverter
class StringToStringWrapperConverter : Converter<String, StringWrapper> {
    override fun convert(source: String): StringWrapper {
        return StringWrapper(source)
    }
}
```

### Register the converters

::: code-group

```kotlin {4-9} [R2DBC]
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .converters(
        listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        )
    )
    .build()
```

```kotlin {4-9} [JDBC]
val kueryClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .converters(
        listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        )
    )
    .build()
```

:::

### Use the type

```kotlin
suspend fun write(str: StringWrapper): Long = kueryClient
    .sql {
        +"INSERT INTO test_table (text) VALUES ($str)"
    }
    .rowsUpdated()

data class Record(
    val text: StringWrapper,
)

suspend fun read(): List<Record> = kueryClient
    .sql {
        +"SELECT * FROM test_table"
    }
    .list()
```

The blocking client uses the same SQL and mapping code without `suspend`.

Conversion is performed per bound value and per mapped column. A non-simple custom type used as the top-level
return type does not automatically use its `@ReadingConverter`; put it in a mapped data class property instead.
Kotlin value classes have additional fetch-side limitations. See [Custom types](/row-mapping#custom-types) and
[Unsupported Kotlin value classes](/row-mapping#unsupported-kotlin-value-classes) in Row Mapping.
