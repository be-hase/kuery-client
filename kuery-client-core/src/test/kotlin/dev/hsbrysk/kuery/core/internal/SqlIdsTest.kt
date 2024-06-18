package dev.hsbrysk.kuery.core.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.core.ClassA
import dev.hsbrysk.kuery.core.internal.SqlIds.removeSuffixes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SqlIdsTest {
    @Test
    fun id() {
        assertThat(ClassA().sql1 {}).isEqualTo("com.example.core.ClassA.sql1")
        runBlocking { assertThat(ClassA().sql2 {}).isEqualTo("com.example.core.ClassA.sql2") }
        assertThat(ClassA.ClassB().sql3 {}).isEqualTo("com.example.core.ClassA.ClassB.sql3")
        assertThat(
            ClassA.ClassB.ClassC().sql4 {},
        ).isEqualTo("com.example.core.ClassA.ClassB.ClassC.sql4")
    }

    @Test
    fun removeSuffixes() {
        assertThat("a.b.c".removeSuffixes(listOf(".b", ".c"))).isEqualTo("a.b")
        assertThat("a.b.c".removeSuffixes(listOf(".a", ".d"))).isEqualTo("a.b.c")
    }
}
