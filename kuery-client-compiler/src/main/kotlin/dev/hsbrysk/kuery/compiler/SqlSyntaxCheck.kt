package dev.hsbrysk.kuery.compiler

/**
 * The value of the `sqlSyntaxCheck` option: it both enables the check and selects how strict it
 * is. Option values are the lowercase entry names.
 *
 * - [GENERIC] parses with a lenient, dialect-agnostic grammar and reports only outright syntax
 *   errors — the fewest false positives.
 * - Every other entry additionally validates the statement against that dialect's feature set,
 *   also flagging features the database does not have (e.g. `ON DUPLICATE KEY UPDATE` under
 *   [POSTGRESQL]). Note [ANSI] is a real, strict feature set, distinct from [GENERIC].
 *
 * Deliberately our own enum rather than the parser's, so the SQL parser backing the check
 * (currently JSqlParser) stays an implementation detail of
 * [dev.hsbrysk.kuery.compiler.fir.SqlSyntaxChecker] and can be swapped without changing any
 * public signature.
 */
enum class SqlSyntaxCheck {
    GENERIC,
    ANSI,
    ORACLE,
    MYSQL,
    SQLSERVER,
    MARIADB,
    POSTGRESQL,
    H2,
    ;

    internal val optionValue: String get() = name.lowercase()

    internal companion object {
        // Derived from the enum so the accepted and the advertised value sets cannot drift.
        val SUPPORTED_VALUES: String = entries.joinToString(", ") { it.optionValue }

        fun fromOptionValueOrNull(value: String): SqlSyntaxCheck? =
            entries.find { it.optionValue.equals(value, ignoreCase = true) }
    }
}
