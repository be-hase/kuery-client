package dev.hsbrysk.kuery.compiler

/**
 * The dialect vocabulary of the `sqlSyntaxCheckDialect` option; option values are the lowercase
 * entry names. Deliberately our own enum rather than the parser's, so the SQL parser backing the
 * check (currently JSqlParser) stays an implementation detail of
 * [dev.hsbrysk.kuery.compiler.fir.SqlSyntaxChecker] and can be swapped without changing any
 * public signature.
 */
enum class SqlDialect {
    ANSI,
    ORACLE,
    MYSQL,
    SQLSERVER,
    MARIADB,
    POSTGRESQL,
    H2,
    ;

    internal val optionValue: String get() = name.lowercase()
}
