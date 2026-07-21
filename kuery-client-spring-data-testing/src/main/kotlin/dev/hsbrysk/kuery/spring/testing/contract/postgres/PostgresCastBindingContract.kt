package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PostgreSQL cast syntax (`::jsonb`, `::uuid`, ...) placed right after an interpolated value
 * relies on Spring's named parameter parsing keeping the `::` suffix intact after the generated
 * placeholder (`:p0::jsonb`). This pins that contract against a real PostgreSQL.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresCastBindingContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpCastTargetsTable() {
        database.execute(
            """
            CREATE TABLE cast_targets (
                id UUID PRIMARY KEY,
                settings JSONB,
                created_at TIMESTAMPTZ
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropCastTargetsTable() {
        database.execute("DROP TABLE IF EXISTS cast_targets")
    }

    @Test
    fun `casts right after placeholders are kept intact`() = runTest {
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
}
