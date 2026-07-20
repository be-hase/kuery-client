package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.list
import org.h2.jdbcx.JdbcDataSource
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.concurrent.TimeUnit

/*
Run settings (fork = 1, 1 warmup + 2 measurement iterations of 2s) come from conventions.jmh, so
Cnt = 2 and there is no error interval; the scores below are a single directional measurement.

Benchmark                                        Mode  Cnt    Score   Error  Units
ValueClassFetchBenchmark.plainDataClassList      avgt    2  275.379          us/op
ValueClassFetchBenchmark.stringScalarList        avgt    2   39.763          us/op
ValueClassFetchBenchmark.valueClassPropertyList  avgt    2  267.776          us/op
ValueClassFetchBenchmark.valueClassScalarList    avgt    2   44.170          us/op
 */

/**
 * End-to-end fetch cost over a 1000-row in-memory H2 table.
 *
 * - [plainDataClassList] / [stringScalarList] exercise the unchanged Spring mapper paths that
 *   existing (non-value-class) users hit — mapper selection is cached per type, so value class
 *   support must not change their per-row cost.
 * - [valueClassPropertyList] / [valueClassScalarList] exercise the new Kotlin-reflection based
 *   mappers, quantifying the per-row cost of value class boxing. With the cached boxing
 *   constructor, the `constructor-impl`/`box-impl` fast path, and the cached converter-precedence
 *   decision, in this measurement value class mapping came out close to the plain Spring mapper:
 *   the value class scalar was modestly slower than a plain String scalar and the value class
 *   property landed in the same range as the plain data class. Single fork, no error interval —
 *   read the numbers as directional.
 */
@OptIn(DelicateKueryClientApi::class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
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
