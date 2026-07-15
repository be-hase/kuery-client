package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Helpers whose receiver is a type parameter bounded by [SqlBuilder] (the natural way to write
 * fluent helpers that return the receiver). The IR rewrite must apply even though the static type
 * of the receiver is not [SqlBuilder] itself.
 */
private fun <T : SqlBuilder> T.whereUserId(userId: Int): T {
    +"WHERE user_id = $userId"
    return this
}

private fun <T : SqlBuilder> T.paging(
    limit: Int,
    offset: Int,
): T {
    add("LIMIT $limit OFFSET $offset")
    return this
}

class GenericReceiverExtensionTest {
    @Test
    fun `generic receiver helper using unaryPlus`() {
        val sql = Sql {
            +"SELECT * FROM users"
            whereUserId(1)
        }
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT * FROM users
                WHERE user_id = :p0
                """.trimIndent(),
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `generic receiver helper using add`() {
        val sql = Sql {
            +"SELECT * FROM users"
            paging(limit = 10, offset = 0)
        }
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT * FROM users
                LIMIT :p0 OFFSET :p1
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", 10),
                    NamedSqlParameter("p1", 0),
                ),
            ),
        )
    }

    @Test
    fun `chained generic receiver helpers`() {
        val sql = Sql {
            +"SELECT * FROM users"
            whereUserId(1).paging(limit = 10, offset = 0)
        }
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT * FROM users
                WHERE user_id = :p0
                LIMIT :p1 OFFSET :p2
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", 1),
                    NamedSqlParameter("p1", 10),
                    NamedSqlParameter("p2", 0),
                ),
            ),
        )
    }
}
