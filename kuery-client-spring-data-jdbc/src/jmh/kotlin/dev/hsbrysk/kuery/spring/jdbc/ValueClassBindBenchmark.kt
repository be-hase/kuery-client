package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.single
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
Benchmark                                    Mode  Cnt   Score   Error  Units
ValueClassBindBenchmark.bindString           avgt    2   6.094          us/op
ValueClassBindBenchmark.bindStringIn100      avgt    2  42.664          us/op
ValueClassBindBenchmark.bindValueClass       avgt    2   5.776          us/op
ValueClassBindBenchmark.bindValueClassIn100  avgt    2  44.864          us/op
 */

/**
 * End-to-end bind cost against an in-memory H2 table.
 *
 * The String variants are the baseline existing (non-value-class) users hit; value class
 * support adds one `isAnnotationPresent` check per bound value / collection element to that
 * path — sub-nanosecond against a multi-microsecond query. The IN variants bind a 100-element
 * collection to amplify the per-element cost (unwrapping adds ~16ns per element).
 */
@OptIn(DelicateKueryClientApi::class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
open class ValueClassBindBenchmark {
    @JvmInline
    value class UserName(val value: String)

    private lateinit var kueryClient: KueryBlockingClient
    private val strings = (1..100).map { "user$it" }
    private val userNames = strings.map { UserName(it) }

    @Setup
    fun setup() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:bind_bench;DB_CLOSE_DELAY=-1")
            user = "sa"
        }
        val jdbcClient = JdbcClient.create(dataSource)
        jdbcClient.sql("DROP TABLE IF EXISTS users").update()
        jdbcClient.sql("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)").update()
        val values = (1..100).joinToString(", ") { "($it, 'user$it')" }
        jdbcClient.sql("INSERT INTO users (id, name) VALUES $values").update()
        kueryClient = SpringJdbcKueryClient.builder().dataSource(dataSource).build()
    }

    @Benchmark
    fun bindString(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql {
                addUnsafe("SELECT COUNT(*) FROM users WHERE name = " + bind("user1"))
            }.single<Long>(),
        )
    }

    @Benchmark
    fun bindValueClass(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql {
                addUnsafe("SELECT COUNT(*) FROM users WHERE name = " + bind(UserName("user1")))
            }.single<Long>(),
        )
    }

    @Benchmark
    fun bindStringIn100(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql {
                addUnsafe("SELECT COUNT(*) FROM users WHERE name IN (" + bind(strings) + ")")
            }.single<Long>(),
        )
    }

    @Benchmark
    fun bindValueClassIn100(blackhole: Blackhole) {
        blackhole.consume(
            kueryClient.sql {
                addUnsafe("SELECT COUNT(*) FROM users WHERE name IN (" + bind(userNames) + ")")
            }.single<Long>(),
        )
    }
}
