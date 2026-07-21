package dev.hsbrysk.kuery.spring.testing

import dev.hsbrysk.kuery.core.KueryClient
import io.micrometer.observation.ObservationRegistry

/**
 * A database an implementation module plugs into a contract test. Each module implements this in
 * its own `src/test` (the jdbc implementation returns its blocking client wrapped in
 * [BlockingKueryClientAdapter]), so contracts are written once against [KueryClient].
 *
 * This interface stays minimal on purpose: only what absorbs the implementation difference.
 * Scenario-specific setup (table DDL, seed data, ...) belongs to the contract classes, built on
 * [execute].
 */
interface ContractDatabase {
    fun kueryClient(
        converters: List<Any> = emptyList(),
        observationRegistry: ObservationRegistry? = null,
    ): KueryClient

    /**
     * Executes DDL / seed SQL directly through the underlying Spring client, bypassing Kuery
     * Client. Deliberately non-suspending so contract `@BeforeEach` / `@AfterEach` methods stay
     * plain methods.
     */
    fun execute(sql: String)
}
