package dev.hsbrysk.kuery.spring.r2dbc

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import org.springframework.jdbc.core.DataClassRowMapper
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/*
Benchmark                                  Mode  Cnt         Score   Error  Units
InitDataClassRowMapperBenchmark.baseline  thrpt    2    615235.453          ops/s
InitDataClassRowMapperBenchmark.cache     thrpt    2  94589146.574          ops/s
 */
@State(Scope.Benchmark)
open class InitDataClassRowMapperBenchmark {
    private val mapperCache = ConcurrentHashMap<KClass<*>, DataClassRowMapper<*>>()

    data class User(
        val userId: Int,
        val username: String,
        val email: String,
    )

    @Benchmark
    fun baseline(blackhole: Blackhole) {
        blackhole.consume(DataClassRowMapper(User::class.java))
    }

    @Benchmark
    fun cache(blackhole: Blackhole) {
        blackhole.consume(
            mapperCache.computeIfAbsent(User::class) { DataClassRowMapper(User::class.java) },
        )
    }
}
