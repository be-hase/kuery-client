# Helpers

## Functions

### `values`

This is a helpful function for performing multi-row inserts.

```kotlin
@Test
fun test() = runTest {
        data class UserParam(val username: String, val email: String?, val age: Int)

        val input = listOf(
            UserParam("user1", "user1@example.com", 1),
            UserParam("user2", null, 2),
            UserParam("user3", "user3@example.com", 3),
        )

        kueryClient.sql {
            +"INSERT INTO users (username, email, age)"
            values(input) { listOf(it.username, it.email, it.age) }
        }.rowsUpdated()
    }
```

## You can also write your own helper

For example, the above `values` function is implemented as follows.

```kotlin
fun SqlBuilder.values(input: List<List<Any?>>) {
    require(input.isNotEmpty()) { "inputted list is empty" }
    val firstSize = input.first().size
    require(input.all { it.size == firstSize }) { "All inputted child lists must have the same size." }
    require(firstSize > 0) { "inputted child list is empty" }

    val placeholders = input.joinToString(", ") { list ->
        list.joinToString(separator = ", ", prefix = "(", postfix = ")") {
            bind(it)
        }
    }
    addUnsafe("VALUES $placeholders")
}

fun <T> SqlBuilder.values(
    input: List<T>,
    transformer: (T) -> List<Any?>,
) {
    values(input.map { transformer(it) })
}
```

Feel free to extend it as you wish.

There may be cases where custom string interpolation is difficult to write. In such situations, please use `addUnsafe`
and `bind`.
(The `values` function above is a good example of this.)
