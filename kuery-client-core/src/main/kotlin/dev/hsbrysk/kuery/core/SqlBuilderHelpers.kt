package dev.hsbrysk.kuery.core

/**
 * Add a `VALUES (...), (...)` clause, binding every element as a parameter.
 *
 * ```kotlin
 * client.sql {
 *     +"INSERT INTO users (username, email)"
 *     values(listOf(
 *         listOf("user1", "user1@example.com"),
 *         listOf("user2", "user2@example.com"),
 *     ))
 * }
 * ```
 *
 * @param input rows of values; each child list corresponds to one row
 * @throws IllegalArgumentException if [input] is empty, a child list is empty,
 * or the child lists do not all have the same size
 */
@OptIn(DelicateKueryClientApi::class)
public fun SqlBuilder.values(input: List<List<Any?>>) {
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

/**
 * Add a `VALUES (...), (...)` clause, converting each element of [input] into a row using [transformer].
 *
 * ```kotlin
 * client.sql {
 *     +"INSERT INTO users (username, email)"
 *     values(users) { listOf(it.username, it.email) }
 * }
 * ```
 *
 * @param input objects to insert; each element corresponds to one row
 * @param transformer converts an element into the list of values for its row
 * @throws IllegalArgumentException if [input] is empty, [transformer] returns an empty list,
 * or the returned lists do not all have the same size
 */
public fun <T> SqlBuilder.values(
    input: List<T>,
    transformer: (T) -> List<Any?>,
) {
    values(input.map { transformer(it) })
}
