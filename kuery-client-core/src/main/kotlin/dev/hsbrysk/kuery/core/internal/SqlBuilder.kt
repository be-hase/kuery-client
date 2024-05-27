package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlDsl
import kotlin.reflect.KClass

internal class SqlBuilder : SqlDsl {
    private val body = StringBuilder()
    private val parameters = mutableListOf<NamedSqlParameter<*>>()

    override fun add(sql: String) {
        body.appendLine(sql)
    }

    override fun String.unaryPlus() {
        add(this)
    }

    override fun <T : Any> bind(
        value: T?,
        kClass: KClass<T>,
    ): String {
        val currentIndex = parameters.size
        val name = PARAMETER_NAME_PREFIX_WITH_COLON + currentIndex
        parameters.add(DefaultNamedSqlParameter(name, value, kClass))
        return name
    }

    fun build(): Sql {
        return DefaultSql(body.toString().trim(), parameters)
    }

    companion object {
        internal const val PARAMETER_NAME_PREFIX_WITH_COLON = ":p"
    }
}
