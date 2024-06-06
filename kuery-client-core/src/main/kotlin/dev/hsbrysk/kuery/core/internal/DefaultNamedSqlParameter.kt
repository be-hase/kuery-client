package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter

internal data class DefaultNamedSqlParameter<T : Any>(
    override val name: String,
    override val value: T?,
) : NamedSqlParameter<T>
