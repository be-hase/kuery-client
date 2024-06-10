package dev.hsbrysk.kuery.core.observation.internal

import dev.hsbrysk.kuery.core.observation.KueryClientFetchContext
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.micrometer.common.docs.KeyName

internal class DefaultKueryClientFetchObservationConvention : KueryClientFetchObservationConvention {
    override fun getLowCardinalityKeyValues(context: KueryClientFetchContext): KeyValues {
        return KeyValues.of(sqlId(context))
    }

    override fun getHighCardinalityKeyValues(context: KueryClientFetchContext): KeyValues {
        return KeyValues.of(sql(context))
    }

    private fun sqlId(context: KueryClientFetchContext): KeyValue {
        return KeyValue.of(SQL_ID_KEY_NAME, context.sqlId)
    }

    private fun sql(context: KueryClientFetchContext): KeyValue {
        return KeyValue.of(SQL_KEY_NAME, context.sql.body)
    }

    companion object {
        private val SQL_ID_KEY_NAME: KeyName = KeyName { "sql.id" }
        private val SQL_KEY_NAME: KeyName = KeyName { "sql" }

        internal fun getLowCardinalityKeyNames(): Array<KeyName> {
            return arrayOf(SQL_ID_KEY_NAME)
        }

        internal fun getHighCardinalityKeyNames(): Array<KeyName> {
            return arrayOf(SQL_KEY_NAME)
        }
    }
}
