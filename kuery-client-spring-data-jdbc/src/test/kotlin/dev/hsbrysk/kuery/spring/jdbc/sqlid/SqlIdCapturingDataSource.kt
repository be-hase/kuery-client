package dev.hsbrysk.kuery.spring.jdbc.sqlid

import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

/**
 * Records [SpringJdbcKueryClient.sqlId] at every statement execution, emulating what a
 * datasource-proxy listener would observe.
 */
internal class SqlIdCapturingDataSource(
    private val delegate: DataSource,
    private val captured: MutableList<String?>,
) : DataSource by delegate {
    override fun getConnection(): Connection = wrapConnection(delegate.connection)

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = wrapConnection(delegate.getConnection(username, password))

    private fun wrapConnection(connection: Connection): Connection = Proxy.newProxyInstance(
        connection.javaClass.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        val result = invoke(method, connection, args)
        if (result is PreparedStatement) wrapStatement(result) else result
    } as Connection

    private fun wrapStatement(statement: PreparedStatement): PreparedStatement = Proxy.newProxyInstance(
        statement.javaClass.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, args ->
        if (method.name.startsWith("execute")) {
            captured.add(SpringJdbcKueryClient.sqlId())
        }
        invoke(method, statement, args)
    } as PreparedStatement

    private fun invoke(
        method: Method,
        target: Any,
        args: Array<Any?>?,
    ): Any? = try {
        if (args == null) method.invoke(target) else method.invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
