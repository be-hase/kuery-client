package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClientBuilder
import io.micrometer.observation.ObservationRegistry
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.convert.CustomConversions
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.core.dialect.DialectResolver
import org.springframework.data.jdbc.core.mapping.JdbcSimpleTypes
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.data.relational.core.dialect.Dialect
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

internal class DefaultSpringJdbcKueryClientBuilder : SpringJdbcKueryClientBuilder {
    private var dataSource: DataSource? = null
    private var converters: List<Any> = emptyList()
    private var observationRegistry: ObservationRegistry? = null
    private var observationConvention: KueryClientFetchObservationConvention? = null
    private var enableAutoSqlIdGeneration: Boolean? = null

    override fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder {
        this.dataSource = dataSource
        return this
    }

    override fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder {
        this.converters = converters
        return this
    }

    override fun observationRegistry(observationRegistry: ObservationRegistry): SpringJdbcKueryClientBuilder {
        this.observationRegistry = observationRegistry
        return this
    }

    override fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringJdbcKueryClientBuilder {
        this.observationConvention = observationConvention
        return this
    }

    override fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringJdbcKueryClientBuilder {
        this.enableAutoSqlIdGeneration = enableAutoSqlIdGeneration
        return this
    }

    override fun build(): KueryBlockingClient {
        val dataSource = requireNotNull(this.dataSource) {
            "Specify dataSource."
        }

        val jdbcClient = jdbcClient(dataSource)
        val conversionService = DefaultConversionService()
        val customConversions = jdbcCustomConversions(dataSource).apply {
            registerConvertersIn(conversionService)
        }
        val enableAutoSqlIdGeneration = enableAutoSqlIdGeneration ?: (observationRegistry != null)

        return DefaultSpringJdbcKueryClient(
            jdbcClient,
            conversionService,
            customConversions,
            observationRegistry,
            observationConvention,
            enableAutoSqlIdGeneration,
        )
    }

    private fun jdbcClient(dataSource: DataSource): JdbcClient = JdbcClient.create(dataSource)

    private fun jdbcCustomConversions(dataSource: DataSource): JdbcCustomConversions {
        val dialect = dialect(dataSource)
        val simpleTypeHolder = if (dialect.simpleTypes().isEmpty()) {
            JdbcSimpleTypes.HOLDER
        } else {
            SimpleTypeHolder(dialect.simpleTypes(), JdbcSimpleTypes.HOLDER)
        }

        return JdbcCustomConversions(
            CustomConversions.StoreConversions.of(simpleTypeHolder, storeConverters(dialect)),
            converters,
        )
    }

    private fun dialect(dataSource: DataSource): Dialect = DialectResolver.getDialect(JdbcTemplate(dataSource))

    private fun storeConverters(dialect: Dialect): List<Any> = buildList {
        addAll(dialect.converters)
        addAll(JdbcCustomConversions.storeConverters())
    }
}
