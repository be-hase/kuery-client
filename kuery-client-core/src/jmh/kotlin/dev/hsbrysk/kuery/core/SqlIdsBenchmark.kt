package dev.hsbrysk.kuery.core

import com.example.core.MockKueryClient
import com.example.core.MockRepository
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

/*
Thanks to the cache, there is little overhead

Benchmark                          Mode  Cnt         Score   Error  Units
SqlIdsBenchmark.autoIdGeneration  thrpt    2  18680410.913          ops/s
SqlIdsBenchmark.baseline          thrpt    2  21090990.061          ops/s
*/
@State(Scope.Benchmark)
open class SqlIdsBenchmark {
    private val repository = MockRepository(MockKueryClient())

    @Benchmark
    fun baseline(blackhole: Blackhole) {
        blackhole.consume(repository.select(Random.nextInt()))
    }

    @Benchmark
    fun autoIdGeneration(blackhole: Blackhole) {
        blackhole.consume(repository.selectWithAutoIdGeneration(Random.nextInt()))
    }
}
