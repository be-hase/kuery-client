package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder

internal class DefaultSqlBuilder : SqlBuilder {
    private val body = StringBuilder()
    private val parameters = mutableListOf<NamedSqlParameter>()

    override fun add(sql: String): Unit = injectByPlugin()

    override fun String.unaryPlus(): Unit = injectByPlugin()

    // Only the developers of the kuery client will use the DefaultSqlBuilder, so we’ll make it opt-in here.
    @OptIn(DelicateKueryClientApi::class)
    override fun addUnsafe(sql: String) {
        body.appendLine(sql)
    }

    // Only the developers of the kuery client will use the DefaultSqlBuilder, so we’ll make it opt-in here.
    @OptIn(DelicateKueryClientApi::class)
    override fun bind(parameter: Any?): String {
        val currentIndex = parameters.size
        parameters.add(DefaultNamedSqlParameter(PARAMETER_NAME_PREFIX + currentIndex, parameter))
        return PARAMETER_NAME_PREFIX_WITH_COLON + currentIndex
    }

    /**
     * This method is effectively public ABI even though the class is internal: the compiler plugin
     * rewrites string interpolation in user code into a direct call to this exact method, so every
     * compiled user artifact links against it. Its name, signature, and class location must not
     * change (guarded by `DefaultSqlBuilderAbiTest`).
     *
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
    ): String {
        val fragmentsSize = fragments.size
        val valuesSize = values.size
        // `StringConcatenationProcessor` should adhere to this assumption, but I'll double-check just to be sure.
        check(fragmentsSize == valuesSize || fragmentsSize == valuesSize + 1) {
            "The number of elements in `fragments`($fragmentsSize) and `values`($valuesSize) is incorrect. " +
                "There might be an issue with the Kotlin compiler plugin."
        }
        return buildString {
            fragments.forEachIndexed { index, fragment ->
                append(fragment)
                if (index < valuesSize) {
                    append(bind(values[index]))
                }
            }
        }
    }

    fun build(): Sql = DefaultSql(body.toString().trim(), parameters.toList())

    companion object {
        internal const val PARAMETER_NAME_PREFIX = "p"
        internal const val PARAMETER_NAME_PREFIX_WITH_COLON = ":$PARAMETER_NAME_PREFIX"

        fun <T> injectByPlugin(): T = error(
            "`SqlBuilder.add`/`String.unaryPlus` must be rewritten by the kuery-client compiler plugin, " +
                "but this call was not. " +
                "Make sure the Gradle plugin `dev.hsbrysk.kuery-client` is applied to the module " +
                "containing this call. " +
                "If it is already applied, this call site is an unsupported usage that the plugin cannot rewrite " +
                "(e.g. calling through a subtype of SqlBuilder, or via a function/reflection reference). " +
                "See https://kuery-client.hsbrysk.dev/getting-started for setup instructions.",
        )
    }
}
