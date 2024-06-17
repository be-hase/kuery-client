package dev.hsbrysk.kuery.core.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SqlIdsTest {
    @Test
    fun id() {
        assertThat(ClassA().sql1()).isEqualTo("dev.hsbrysk.kuery.core.internal.ClassA.sql1")
        runBlocking { assertThat(ClassA().sql2()).isEqualTo("dev.hsbrysk.kuery.core.internal.ClassA.sql2") }
        assertThat(ClassA.ClassB().sql3()).isEqualTo("dev.hsbrysk.kuery.core.internal.ClassA.ClassB.sql3")
        assertThat(ClassA.ClassB.ClassC().sql4()).isEqualTo("dev.hsbrysk.kuery.core.internal.ClassA.ClassB.ClassC.sql4")
    }
}

internal class ClassA {
    fun sql1(): String {
        return sqlId {}
    }

    suspend fun sql2(): String {
        return run {
            sqlId {}
        }
    }

    internal class ClassB {
        fun sql3(): String {
            return sqlId {}
        }

        internal class ClassC {
            fun sql4(): String {
                return sqlId {}
            }
        }
    }
}

private fun sqlId(block: SqlBuilder.() -> Unit) = block.id()
