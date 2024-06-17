package com.example.core

import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class ClassA {
    fun sql1(block: SqlBuilder.() -> Unit): String {
        return block.id()
    }

    suspend fun sql2(block: SqlBuilder.() -> Unit): String {
        return withContext(Dispatchers.Default) {
            delay(10)
            withContext(Dispatchers.IO) {
                block.id()
            }
        }
    }

    internal class ClassB {
        fun sql3(block: SqlBuilder.() -> Unit): String {
            return block.id()
        }

        internal class ClassC {
            fun sql4(block: SqlBuilder.() -> Unit): String {
                return block.id()
            }
        }
    }
}
