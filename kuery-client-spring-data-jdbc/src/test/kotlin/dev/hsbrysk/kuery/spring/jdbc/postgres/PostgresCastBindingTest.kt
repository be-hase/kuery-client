package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PostgreSQL cast syntax (`::jsonb`, `::uuid`, ...) placed right after an interpolated value
 * relies on Spring's named parameter parsing keeping the `::` suffix intact after the generated
 * placeholder (`:p0::jsonb`). This pins that contract against a real PostgreSQL.
 */
class PostgresCastBindingTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() {
        postgres.jdbcClient.sql(
            """
            CREATE TABLE cast_targets (
                id UUID PRIMARY KEY,
                settings JSONB,
                created_at TIMESTAMPTZ
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        postgres.jdbcClient.sql("DROP TABLE cast_targets").update()
    }

    @Test
    fun `casts right after placeholders are kept intact`() {
        // given
        val id = "0f14d0ab-9605-4a62-a9e4-5ed26688389b"
        val settings = """{"theme": "dark"}"""
        val createdAt = "2026-01-01 00:00:00+00"

        // when
        val inserted = kueryClient
            .sql {
                +"INSERT INTO cast_targets (id, settings, created_at)"
                +"VALUES ($id::uuid, $settings::jsonb, $createdAt::timestamptz)"
            }
            .rowsUpdated()

        // then
        assertThat(inserted).isEqualTo(1L)

        val record = kueryClient
            .sql {
                +"SELECT id::text AS id, settings::text AS settings FROM cast_targets"
                +"WHERE id = $id::uuid AND created_at = $createdAt::timestamptz"
            }
            .singleMap()
        assertThat(record["id"]).isEqualTo(id)
        assertThat(record["settings"]).isEqualTo(settings)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
