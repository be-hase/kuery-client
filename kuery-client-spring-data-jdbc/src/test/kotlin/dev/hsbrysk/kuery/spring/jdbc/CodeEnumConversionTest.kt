package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

class CodeEnumConversionTest {
    private val kueryClient = mysql.kueryClient(
        listOf(
            IntCodeEnumWritingConverter(),
            IntCodeEnumReadingConverter(),
            StringCodeEnumWritingConverter(),
            StringCodeEnumReadingConverter(),
        ),
    )

    @BeforeEach
    fun beforeEach() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE `code_enum`
            (
                `id` BIGINT AUTO_INCREMENT,
                `int_enum` INT DEFAULT NULL,
                `string_enum` VARCHAR(255) DEFAULT NULL,
                PRIMARY KEY (`id`)
            ) ENGINE = InnoDB
              DEFAULT CHARSET = utf8mb4
              COLLATE = utf8mb4_bin;
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun afterEach() {
        mysql.jdbcClient.sql("DROP TABLE code_enum").update()
    }

    interface CodeEnum<T> {
        val code: T

        companion object {
            fun <T, E : CodeEnum<T>> getByCode(
                code: T,
                clazz: Class<E>,
            ): E? {
                return clazz.enumConstants.firstOrNull { code == it.code }
            }
        }
    }

    interface IntCodeEnum : CodeEnum<Int>

    interface StringCodeEnum : CodeEnum<String>

    enum class SampleIntCodeEnum(override val code: Int) : IntCodeEnum {
        HOGE(10),
    }

    enum class SampleStringCodeEnum(override val code: String) : StringCodeEnum {
        BAR("hoge"),
    }

    data class Record(
        val intEnum: SampleIntCodeEnum,
        val stringEnum: SampleStringCodeEnum,
    )

    @WritingConverter
    class IntCodeEnumWritingConverter : Converter<IntCodeEnum, Int> {
        override fun convert(source: IntCodeEnum): Int {
            return source.code
        }
    }

    @ReadingConverter
    class IntCodeEnumReadingConverter : ConverterFactory<Int, IntCodeEnum> {
        override fun <E : IntCodeEnum> getConverter(targetType: Class<E>): Converter<Int, E> {
            return Converter {
                CodeEnum.getByCode(it, targetType)
            }
        }
    }

    @WritingConverter
    class StringCodeEnumWritingConverter : Converter<StringCodeEnum, String> {
        override fun convert(source: StringCodeEnum): String {
            return source.code
        }
    }

    @ReadingConverter
    class StringCodeEnumReadingConverter : ConverterFactory<String, StringCodeEnum> {
        override fun <E : StringCodeEnum> getConverter(targetType: Class<E>): Converter<String, E> {
            return Converter {
                CodeEnum.getByCode(it, targetType)
            }
        }
    }

    @Test
    fun test() {
        kueryClient.sql {
            +"INSERT INTO code_enum (int_enum, string_enum)"
            +"VALUES (${SampleIntCodeEnum.HOGE}, ${SampleStringCodeEnum.BAR})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM code_enum"
        }.single()

        assertThat(record.intEnum).isEqualTo(SampleIntCodeEnum.HOGE)
        assertThat(record.stringEnum).isEqualTo(SampleStringCodeEnum.BAR)
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
