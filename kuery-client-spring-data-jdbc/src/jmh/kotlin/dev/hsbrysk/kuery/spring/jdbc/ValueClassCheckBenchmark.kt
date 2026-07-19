package dev.hsbrysk.kuery.spring.jdbc

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/*
Benchmark                                                 Mode  Cnt  Score   Error  Units
ValueClassCheckBenchmark.classValueCachedOnPlainClass     avgt    2  1.347          ns/op
ValueClassCheckBenchmark.isAnnotationPresentOnPlainClass  avgt    2  0.903          ns/op
ValueClassCheckBenchmark.isAnnotationPresentOnValueClass  avgt    2  1.569          ns/op
 */

/**
 * Measures the per-bind overhead that value class support adds for users who do NOT use value
 * classes: one `isAnnotationPresent(JvmInline)` call per bound value / collection element.
 * The ClassValue variant is the candidate replacement if the annotation lookup turns out to be
 * expensive — it is not: the JDK caches annotation data per class, so the direct check is
 * sub-nanosecond and a ClassValue would actually be slower.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
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
