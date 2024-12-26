package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder

internal class DefaultSqlBuilder : SqlBuilder {
    private val body = StringBuilder()
    private val parameters = mutableListOf<NamedSqlParameter>()

    override fun add(sql: String): Unit = injectByPlugin()

    override fun String.unaryPlus(): Unit = injectByPlugin()

    override fun addUnsafe(sql: String) {
        body.appendLine(sql)
    }

    override fun bind(parameter: Any?): String {
        val currentIndex = parameters.size
        parameters.add(DefaultNamedSqlParameter(PARAMETER_NAME_PREFIX + currentIndex, parameter))
        return PARAMETER_NAME_PREFIX_WITH_COLON + currentIndex
    }

    /**
     * @param fragments It refers to string parts.
     * @param values It refers to the values of string interpolation.
     * e.g.
     * ```
     * """a${1}b""" -> fragments=["a", "b"], values=[1]
     * """${1}a""" -> fragments=["", "a"], values=[1]
     * """a${1}""" -> fragments=["a"], values=[1]
     * """a${1}${2}${3}""" -> fragments=["a", "", ""], values=[1, 2, 3]
     * ```
     */
    @Suppress("unused") // used by compiler plugin
    fun interpolate(
        fragments: List<String>,
        values: List<Any?>,
    ): String = buildString {
        fragments.forEachIndexed { index, fragment ->
            append(fragment)
            if (index < values.size) {
                append(bind(values[index]))
            }
        }
    }

    fun build(): Sql = DefaultSql(body.toString().trim(), parameters)

    companion object {
        internal const val PARAMETER_NAME_PREFIX = "p"
        internal const val PARAMETER_NAME_PREFIX_WITH_COLON = ":$PARAMETER_NAME_PREFIX"

        fun <T> injectByPlugin(): T =
            error("kuery-client-compiler plugin is not loaded or you are using an unsupported usage.")
    }
}
