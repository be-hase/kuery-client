package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Common query parts written as SqlBuilder extension functions (the documented helper style).
 * This module compiles with allWarningsAsErrors, so this file also guards against
 * KUERY_UNSAFE_SQL_STRING false positives for this style.
 */
private data class UserFilter(
    val tenantId: Int,
    val name: String?,
    val minAge: Int?,
)

private fun SqlBuilder.whereUser(filter: UserFilter) {
    +"WHERE tenant_id = ${filter.tenantId}"
    filter.name?.let { +"AND name = $it" }
    filter.minAge?.let { +"AND age >= $it" }
}

private fun SqlBuilder.paging(
    limit: Int,
    offset: Int,
) {
    add("LIMIT $limit OFFSET $offset")
}

class ExtensionHelperTest {
    @Test
    fun `extension function helpers are interpolated as usual`() {
        // when
        val sql = Sql {
            +"SELECT * FROM users"
            whereUser(UserFilter(tenantId = 1, name = null, minAge = 20))
            paging(limit = 10, offset = 0)
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT * FROM users
                WHERE tenant_id = :p0
                AND age >= :p1
                LIMIT :p2 OFFSET :p3
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", 1),
                    NamedSqlParameter("p1", 20),
                    NamedSqlParameter("p2", 10),
                    NamedSqlParameter("p3", 0),
                ),
            ),
        )
    }
}
