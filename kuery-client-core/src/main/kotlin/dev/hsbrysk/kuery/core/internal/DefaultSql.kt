package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql

internal data class DefaultSql(
    override val body: String,
    override val parameters: List<NamedSqlParameter<*>>,
) : Sql
