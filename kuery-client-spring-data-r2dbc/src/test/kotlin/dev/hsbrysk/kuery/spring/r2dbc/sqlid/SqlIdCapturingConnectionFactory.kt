package dev.hsbrysk.kuery.spring.r2dbc.sqlid

import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

/**
 * Records [SpringR2dbcKueryClient.sqlId] (read from the Reactor context) at every statement
 * execution, emulating what an r2dbc-proxy listener would observe.
 */
internal class SqlIdCapturingConnectionFactory(
    private val delegate: ConnectionFactory,
    private val captured: MutableList<String?>,
) : ConnectionFactory by delegate {
    override fun create(): Publisher<out Connection> = Flux.from(delegate.create())
        .map { SqlIdCapturingConnection(it, captured) }
}

private class SqlIdCapturingConnection(
    private val delegate: Connection,
    private val captured: MutableList<String?>,
) : Connection by delegate {
    override fun createStatement(sql: String): Statement =
        SqlIdCapturingStatement(delegate.createStatement(sql), captured)
}

// Fluent methods must return `this` (not the delegate) so that execute() below stays wrapped.
private class SqlIdCapturingStatement(
    private val delegate: Statement,
    private val captured: MutableList<String?>,
) : Statement {
    override fun add(): Statement = apply { delegate.add() }

    override fun bind(
        index: Int,
        value: Any,
    ): Statement = apply { delegate.bind(index, value) }

    override fun bind(
        name: String,
        value: Any,
    ): Statement = apply { delegate.bind(name, value) }

    override fun bindNull(
        index: Int,
        type: Class<*>,
    ): Statement = apply { delegate.bindNull(index, type) }

    override fun bindNull(
        name: String,
        type: Class<*>,
    ): Statement = apply { delegate.bindNull(name, type) }

    override fun fetchSize(rows: Int): Statement = apply { delegate.fetchSize(rows) }

    override fun returnGeneratedValues(vararg columns: String): Statement =
        apply { delegate.returnGeneratedValues(*columns) }

    override fun execute(): Publisher<out Result> = Flux.deferContextual { context ->
        captured.add(SpringR2dbcKueryClient.sqlId(context))
        Flux.from(delegate.execute())
    }
}
