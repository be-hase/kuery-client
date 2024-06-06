package dev.hsbrysk.kuery.core

interface KueryClient {
    fun sql(block: SqlDsl.() -> Unit): FetchSpec
}
