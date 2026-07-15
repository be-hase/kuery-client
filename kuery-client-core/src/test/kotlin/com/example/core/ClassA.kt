package com.example.core

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(KueryClientInternalApi::class)
internal class ClassA {
    fun sql1(block: SqlBuilder.() -> Unit): String = block.id()

    @Suppress("InjectDispatcher")
    suspend fun sql2(block: SqlBuilder.() -> Unit): String = withContext(Dispatchers.Default) {
        delay(10)
        withContext(Dispatchers.IO) {
            block.id()
        }
    }

    fun sql5(block: SqlBuilder.() -> Unit): String = execute { block.id() }

    // Simulates a class-compiled lambda (e.g. -Xlambdas=class), which has an `invoke` method.
    fun sql6(block: SqlBuilder.() -> Unit): String = execute(
        object : () -> String {
            override fun invoke(): String = block.id()
        },
    )

    private fun <T> execute(action: () -> T): T = action()

    internal class ClassB {
        fun sql3(block: SqlBuilder.() -> Unit): String = block.id()

        internal class ClassC {
            fun sql4(block: SqlBuilder.() -> Unit): String = block.id()
        }
    }
}
