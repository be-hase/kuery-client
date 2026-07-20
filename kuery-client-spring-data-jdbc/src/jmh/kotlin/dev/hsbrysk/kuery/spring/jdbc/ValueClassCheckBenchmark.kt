package dev.hsbrysk.kuery.spring.jdbc

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/*
Run settings (fork = 1, 1 warmup + 2 measurement iterations of 2s) come from conventions.jmh, so
Cnt = 2 and there is no error interval; the scores below are a single directional measurement.

Benchmark                                                 Mode  Cnt  Score   Error  Units
ValueClassCheckBenchmark.classValueCachedOnPlainClass     avgt    2  1.347          ns/op
ValueClassCheckBenchmark.isAnnotationPresentOnPlainClass  avgt    2  0.903          ns/op
ValueClassCheckBenchmark.isAnnotationPresentOnValueClass  avgt    2  1.569          ns/op
 */

/**
 * Measures the per-bind overhead that value class support adds for users who do NOT use value
 * classes: one `isAnnotationPresent(JvmInline)` call per bound value / collection element.
 * The ClassValue variant is the candidate replacement if the annotation lookup turns out to be
 * expensive; in this measurement it was not — the JDK caches annotation data per class, so the
 * direct check came out sub-nanosecond and the ClassValue was, if anything, slightly slower.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class ValueClassCheckBenchmark {
    @JvmInline
    value class UserName(val value: String)

    private val cachedIsValueClass = object : ClassValue<Boolean>() {
        override fun computeValue(type: Class<*>): Boolean = type.isAnnotationPresent(JvmInline::class.java)
    }

    @Benchmark
    fun isAnnotationPresentOnPlainClass(): Boolean = String::class.java.isAnnotationPresent(JvmInline::class.java)

    @Benchmark
    fun isAnnotationPresentOnValueClass(): Boolean = UserName::class.java.isAnnotationPresent(JvmInline::class.java)

    @Benchmark
    fun classValueCachedOnPlainClass(): Boolean = cachedIsValueClass.get(String::class.java)
}
