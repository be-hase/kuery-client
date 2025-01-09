package dev.hsbrysk.kuery.core

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
