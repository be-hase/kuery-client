package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.autotrim.AutoTrimQueries
import com.example.runtimetrim.RuntimeTrimQueries
import org.junit.jupiter.api.Test

class QueryFixturesTest {
    @Test
    fun `the folded and runtime-trimmed fixtures build the same query`() {
        // The JMH comparison is only meaningful while the two modules build the exact same Sql;
        // this guards the fixtures against being edited independently.
        assertThat(AutoTrimQueries.update(1, "name", 18, "address"))
            .isEqualTo(RuntimeTrimQueries.update(1, "name", 18, "address"))
    }
}
