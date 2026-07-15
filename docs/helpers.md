---
description: Built-in values helper for multi-row inserts and guidance on writing custom SqlBuilder extension helpers using addUnsafe / bind.
---

# Helpers

## Functions

### `values`

This is a helpful function for performing multi-row inserts.

```kotlin
data class UserParam(val username: String, val email: String?, val age: Int)

suspend fun insertMany(params: List<UserParam>): Long = kueryClient
    .sql {
        +"INSERT INTO users (username, email, age)"
        values(params) { listOf(it.username, it.email, it.age) }
    }
    .rowsUpdated()

// INSERT INTO users (username, email, age) VALUES (:p0, :p1, :p2), (:p3, :p4, :p5), ...
```

## You can also write your own helper

For example, the above `values` function is implemented as follows.

```kotlin
@OptIn(DelicateKueryClientApi::class)
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

Note that `addUnsafe` and `bind` are annotated with `@DelicateKueryClientApi` and require an explicit opt-in, since
misusing them can lead to SQL injection. See [Compiler Safety Check](/compiler-safety-check) for details.
