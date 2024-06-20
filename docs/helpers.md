# Helpers

## Functions

### `values`

This is a helpful function for performing multi-row inserts.

```kotlin
@Test
fun `test with transformer`() = runTest {
        data class UserParam(val username: String, val email: String?, val age: Int)

        val input = listOf(
            UserParam("user1", "user1@example.com", 1),
            UserParam("user2", null, 2),
            UserParam("user3", "user3@example.com", 3),
        )

        kueryClient.sql {
            +"INSERT INTO users (username, email, age)"
            +values(input) { listOf(it.username, it.email, it.age) }
        }.rowsUpdated()
    }
```

```kotlin
@Test
fun `test with transformer in string interpolation`() = runTest {
        data class UserParam(val username: String, val email: String?, val age: Int)

        val input = listOf(
            UserParam("user1", "user1@example.com", 1),
            UserParam("user2", null, 2),
            UserParam("user3", "user3@example.com", 3),
        )

        kueryClient.sql {
            +"INSERT INTO users (username, email, age) ${values(input) { listOf(it.username, it.email, it.age) }}"
        }.rowsUpdated()
    }
```

## You can also write your own helper

For example, the above `values` function is implemented as follows.

```kotlin
fun SqlBuilder.values(input: List<List<Any?>>): String {
    require(input.isNotEmpty()) { "inputted list is empty" }
    val firstSize = input.first().size
    require(input.all { it.size == firstSize }) { "All inputted child lists must have the same size." }
    require(firstSize > 0) { "inputted child list is empty" }

    val placeholders = input.joinToString(", ") { list ->
        list.joinToString(separator = ", ", prefix = "(", postfix = ")") {
            bind(it)
        }
    }
    return "VALUES $placeholders"
}

fun <T> SqlBuilder.values(
    input: List<T>,
    transformer: (T) -> List<Any?>,
): String {
    return values(input.map { transformer(it) })
}
```

Feel free to extend it as you wish.

However, if you provide your own helper functions, they might violate the detekt custom rule. To avoid this, please add
allowRegexes to the detekt custom rule.

```yaml
kuery-client:
  StringInterpolation:
    active: true
    allowRegexes:
      - ^yourFunction\(.+\)$
  UseStringLiteral:
    active: true
    allowRegexes:
      - ^yourFunction\(.+\)$
```
