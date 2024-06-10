# Type Conversion

By using Spring Type Conversion, you can support your own custom types.
https://docs.spring.io/spring-framework/reference/core/validation/convert.html

## Examples

### Custom type used as a sample

```kotlin
data class StringWrapper(val value: String)
```

### Prepare Type Converters

```kotlin
class StringWrapperToStringConverter : Converter<StringWrapper, String> {
    override fun convert(source: StringWrapper): String {
        return source.value
    }
}

class StringToStringWrapperConverter : Converter<String, StringWrapper> {
    override fun convert(source: String): StringWrapper {
        return StringWrapper(source)
    }
}
```

### Specify the converters when creating the `KueryClient`

```kotlin
// e.g. In the case of kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .converters(listOf(StringWrapperToStringConverter(), StringToStringWrapperConverter()))
    .build()
```

### Let's Try

```kotlin
suspend fun write() {
    kueryClient
        .sql {
            +"INSERT INTO converter (text) VALUES (${bind(StringWrapper("hoge"))})"
        }
        .rowsUpdated()
}

suspend fun read() {
    data class Record(
        val text: StringWrapper,
    )

    val records: List<Record> = kueryClient
        .sql {
            +"SELECT * FROM converter"
        }
        .list()
}
```
