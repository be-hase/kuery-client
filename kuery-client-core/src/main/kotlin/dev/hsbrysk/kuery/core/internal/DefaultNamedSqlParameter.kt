package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.NamedSqlParameter
import kotlin.reflect.KClass

internal data class DefaultNamedSqlParameter<T : Any>(
    override val name: String,
    override val value: T?,
    override val kClass: KClass<T>,
) : NamedSqlParameter<T>
