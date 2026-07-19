package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.list
import org.h2.jdbcx.JdbcDataSource
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.concurrent.TimeUnit

/*
Benchmark                                        Mode  Cnt    Score   Error  Units
ValueClassFetchBenchmark.plainDataClassList      avgt    2  313.668          us/op
ValueClassFetchBenchmark.stringScalarList        avgt    2   38.732          us/op
ValueClassFetchBenchmark.valueClassPropertyList  avgt    2  301.377          us/op
ValueClassFetchBenchmark.valueClassScalarList    avgt    2   81.169          us/op
 */

/**
 * End-to-end fetch cost over a 1000-row in-memory H2 table.
 *
 * - [plainDataClassList] / [stringScalarList] exercise the unchanged Spring mapper paths that
 *   existing (non-value-class) users hit — mapper selection is cached per type, so value class
 *   support must not change their per-row cost.
 * - [valueClassPropertyList] / [valueClassScalarList] exercise the new Kotlin-reflection based
 *   mappers, quantifying the per-row cost of value class boxing. With the cached boxing
 *   constructor, value class property mapping is on par with the plain Spring mapper; the
 *   scalar path adds ~40ns per row over a plain String scalar for the boxing itself.
 */
@OptIn(DelicateKueryClientApi::class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
open class ValueClassFetchBenchmark {
    @JvmInline
    value class UserName(val value: String)

    data class PlainUser(
        val id: Long,
        val name: String,
    )

    data class VcUser(
        val id: Long,
        val name: UserName,
    )

    private lateinit var kueryClient: KueryBlockingClient

    @Setup
    fun setup() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:fetch_bench;DB_CLOSE_DELAY=-1")
            user = "sa"
        }
        val jdbcClient = JdbcClient.create(dataSource)
        jdbcClient.sql("DROP TABLE IF EXISTS users").update()
        jdbcClient.sql("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)").update()
        val values = (1..1000).joinToString(", ") { "($it, 'user$it')" }
        jdbcClient.sql("INSERT INTO users (id, name) VALUES $values").update()
        kueryClient = SpringJdbcKueryClient.builder().dataSource(dataSource).build()
    }

    @Benchmark
    fun plainDataClassList(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql { addUnsafe("SELECT id, name FROM users") }.list<PlainUser>(),
        )
    }

    @Benchmark
    fun valueClassPropertyList(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql { addUnsafe("SELECT id, name FROM users") }.list<VcUser>(),
        )
    }

    @Benchmark
    fun stringScalarList(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql { addUnsafe("SELECT name FROM users") }.list<String>(),
        )
    }

    @Benchmark
    fun valueClassScalarList(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql { addUnsafe("SELECT name FROM users") }.list<UserName>(),
        )
    }
}
