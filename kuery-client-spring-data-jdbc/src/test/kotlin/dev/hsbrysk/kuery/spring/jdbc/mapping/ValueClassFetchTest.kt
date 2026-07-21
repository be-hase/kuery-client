package dev.hsbrysk.kuery.spring.jdbc.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.sequence
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mapping.ValueClassFetchContract
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.jdbc.IncorrectResultSetColumnCountException

class ValueClassFetchTest : ValueClassFetchContract() {
    override val database get() = h2

    override val exceptionProfile get() = jdbcExceptionProfile

    private val blockingClient = h2.blockingClient()

    @JvmInline
    value class Flag(val value: Boolean)

    @Test
    fun `sequence keeps a SQL NULL value class scalar as a null element`() {
        // The scalar mapper maps a SQL NULL row to a null element; sequence() must preserve it
        // (non-null underlying type), like list().
        // given
        h2.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<UserName?> = blockingClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.sequence<UserName>().toList()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `sequence maps a nullable-underlying value class scalar SQL NULL to an inner null`() {
        // given
        h2.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<OptionalUserName> = blockingClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.sequence<OptionalUserName>().toList()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    @Test
    fun `fetching a value class scalar from a multi-column result fails`() {
        // Same contract as the simple scalar path (SingleColumnRowMapper). The r2dbc scalar
        // path does not validate the column count, so this test is jdbc-only.
        // given
        h2.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        assertFailure {
            blockingClient.sql {
                +"SELECT id, text FROM converter"
            }.list<UserName>()
        }.isInstanceOf(IncorrectResultSetColumnCountException::class)
    }

    @Test
    fun `driver coercion remains available when no reading converter matches`() {
        // The ConversionService has no Long -> Boolean converter; the driver's typed retrieval
        // (ResultSet.getBoolean) must still coerce the BIGINT value to the Boolean underlying.
        // jdbc-only: r2dbc-h2 does not coerce a numeric column to Boolean via readable.get.
        // given
        h2.execute("INSERT INTO converter (text) VALUES ('x')")

        // when & then
        val result: Flag = blockingClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(Flag(true))
    }

    @Test
    fun `a reading converter from the driver typed value wins over automatic boxing`() {
        // The BIGINT id has no Converter<Long, StringId>, so it is retrieved typed as the String
        // underlying (ResultSet.getString); a Converter<String, StringId> keyed on that produced
        // type must still win over automatic boxing (which would yield StringId("1")).
        // jdbc-only: r2dbc-h2 does not coerce a numeric column to a String via readable.get, so the
        // driver never produces the typed String there (same limitation as the Boolean case above).
        // given
        val blockingClient = h2.blockingClient(listOf(StringToStringIdConverter()))
        h2.execute("INSERT INTO converter (text) VALUES ('x')")

        // when & then
        val result: StringId = blockingClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(StringId("typed:1"))
    }

    @ReadingConverter
    class StringToStringIdConverter : Converter<String, StringId> {
        override fun convert(source: String): StringId = StringId("typed:$source")
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
