package dev.hsbrysk.kuery.core

fun SqlBuilder.values(input: List<List<Any?>>): String {
    require(input.isNotEmpty())
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
