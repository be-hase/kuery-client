package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter

internal data class DefaultNamedSqlParameter(
    override val name: String,
    override val value: Any?,
) : NamedSqlParameter
