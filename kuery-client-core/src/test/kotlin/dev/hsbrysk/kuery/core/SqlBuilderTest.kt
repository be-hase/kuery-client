package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class SqlBuilderTest {
    @Test
    fun id() {
        assertThat(ClassA().sql1()).isEqualTo("dev.hsbrysk.kuery.core.ClassA.sql1")
        assertThat(ClassA().sql2()).isEqualTo("dev.hsbrysk.kuery.core.ClassA.sql2")
        assertThat(ClassA.ClassB().sql3()).isEqualTo("dev.hsbrysk.kuery.core.ClassA.ClassB.sql3")
        assertThat(ClassA.ClassB.ClassC().sql4()).isEqualTo("dev.hsbrysk.kuery.core.ClassA.ClassB.ClassC.sql4")
    }
}

internal class ClassA {
    fun sql1(): String {
        return sqlId {}
    }

    fun sql2(): String {
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
