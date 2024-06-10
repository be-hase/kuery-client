# Type Conversion

By using Spring Type Conversion, you can support your own custom types.
https://docs.spring.io/spring-framework/reference/core/validation/convert.html

## Examples

### Prepare Type Converters

```kotlin
data class StringWrapper(val value: String)

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
// e.g. kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .converters(listOf(StringWrapperToStringConverter(), StringToStringWrapperConverter()))
    .build()
```
