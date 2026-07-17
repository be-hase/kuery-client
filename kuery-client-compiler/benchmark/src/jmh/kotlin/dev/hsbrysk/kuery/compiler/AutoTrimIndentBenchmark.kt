package dev.hsbrysk.kuery.compiler

import com.example.autotrim.AutoTrimQueries
import com.example.runtimetrim.RuntimeTrimQueries
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

/*
Folding removes the per-execution trim entirely — and even beats the untrimmed template,
because the folded fragments no longer carry the source indentation into the body builder.

Benchmark                                                   Mode  Cnt        Score   Error  Units
AutoTrimIndentBenchmark.autoTrimIndentFoldedAtCompileTime  thrpt    2  9050269.101          ops/s
AutoTrimIndentBenchmark.explicitTrimIndentAtRuntime        thrpt    2  1967411.321          ops/s
AutoTrimIndentBenchmark.untrimmedBaseline                  thrpt    2  7433947.976          ops/s
*/

/**
 * Measures what `autoTrimIndent` saves: building the same multi-line UPDATE (4 bind values)
 * from a module compiled with the option on (trim folded at compile time) versus the status quo
 * of an explicit `.trimIndent()` running at runtime, with the untrimmed template as the floor.
 * No database is involved — `Sql { }` is exactly the SQL-building work a client performs per
 * execution.
 */
@State(Scope.Benchmark)
open class AutoTrimIndentBenchmark {
    @Benchmark
    fun explicitTrimIndentAtRuntime(blackhole: Blackhole) {
        blackhole.consume(RuntimeTrimQueries.update(Random.nextInt(), NAME, AGE, ADDRESS))
    }

    @Benchmark
    fun autoTrimIndentFoldedAtCompileTime(blackhole: Blackhole) {
        blackhole.consume(AutoTrimQueries.update(Random.nextInt(), NAME, AGE, ADDRESS))
    }

    @Benchmark
    fun untrimmedBaseline(blackhole: Blackhole) {
        blackhole.consume(RuntimeTrimQueries.updateUntrimmed(Random.nextInt(), NAME, AGE, ADDRESS))
    }

    companion object {
        private const val NAME = "name"
        private const val AGE = 18
        private const val ADDRESS = "address"
    }
}
