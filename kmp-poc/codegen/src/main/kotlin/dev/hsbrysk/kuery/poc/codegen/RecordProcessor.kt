package dev.hsbrysk.kuery.poc.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

class RecordProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RecordProcessor(environment.codeGenerator, environment.logger)
}

/**
 * PoC: generates a sqlx4k `RowMapper` object (`<Name>RowMapper`, same package) for every
 * data class annotated with `@Record`.
 *
 * Unlike sqlx4k-codegen's `@Table`, no table name and no `@Id` are required: row mapping only
 * needs the column names, which are derived from the property names via snake_case conversion
 * (the same convention sqlx4k uses).
 */
class RecordProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(RECORD_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { generateRowMapper(it) }
        return emptyList()
    }

    private fun generateRowMapper(record: KSClassDeclaration) {
        val packageName = record.packageName.asString()
        val recordName = record.simpleName.asString()
        val constructor = record.primaryConstructor
        if (constructor == null) {
            logger.error("@Record class must have a primary constructor", record)
            return
        }

        val assignments = constructor.parameters.map { parameter ->
            val property = parameter.name!!.asString()
            val accessor = accessor(parameter.type.resolve(), parameter)
            """$property = row.get("${property.toSnakeCase()}").$accessor"""
        }
        val content = buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import io.github.smyrgeorge.sqlx4k.ResultSet")
            appendLine("import io.github.smyrgeorge.sqlx4k.RowMapper")
            appendLine("import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry")
            appendLine("import io.github.smyrgeorge.sqlx4k.impl.extensions.*")
            appendLine()
            appendLine("object ${recordName}RowMapper : RowMapper<$recordName> {")
            appendLine("    override fun map(row: ResultSet.Row, converters: ValueEncoderRegistry): $recordName = $recordName(")
            assignments.forEach { appendLine("        $it,") }
            appendLine("    )")
            appendLine("}")
        }
        codeGenerator
            .createNewFile(Dependencies(false, record.containingFile!!), packageName, "${recordName}RowMapper")
            .use { it.write(content.toByteArray()) }
    }

    private fun accessor(
        type: KSType,
        parameter: KSValueParameter,
    ): String {
        val base = when (val qualified = type.declaration.qualifiedName?.asString()) {
            "kotlin.String" -> "asString"
            "kotlin.Int" -> "asInt"
            "kotlin.Long" -> "asLong"
            "kotlin.Short" -> "asShort"
            "kotlin.Boolean" -> "asBoolean"
            "kotlin.Double" -> "asDouble"
            "kotlin.Float" -> "asFloat"
            else -> {
                logger.error("@Record: unsupported property type $qualified (the PoC supports basic scalars only)", parameter)
                "asString"
            }
        }
        return if (type.isMarkedNullable) "${base}OrNull()" else "$base()"
    }

    private fun String.toSnakeCase(): String =
        replace(SNAKE_BOUNDARY) { "${it.groupValues[1]}_${it.groupValues[2]}" }.lowercase()

    companion object {
        private const val RECORD_ANNOTATION = "dev.hsbrysk.kuery.annotation.Record"
        private val SNAKE_BOUNDARY = Regex("([a-z0-9])([A-Z])")
    }
}
