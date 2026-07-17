package dev.hsbrysk.kuery.core.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.core.ClassA
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import dev.hsbrysk.kuery.core.internal.SqlIds.removeSuffixes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

@OptIn(KueryClientInternalApi::class)
class SqlIdsTest {
    @Test
    fun `id resolves the fully qualified class and method name of the call site`() {
        assertThat(ClassA().sql1 {}).isEqualTo("com.example.core.ClassA.sql1")
        runBlocking { assertThat(ClassA().sql2 {}).isEqualTo("com.example.core.ClassA.sql2") }
        assertThat(ClassA.ClassB().sql3 {}).isEqualTo("com.example.core.ClassA.ClassB.sql3")
        assertThat(
            ClassA.ClassB.ClassC().sql4 {},
        ).isEqualTo("com.example.core.ClassA.ClassB.ClassC.sql4")
        assertThat(ClassA().sql5 {}).isEqualTo("com.example.core.ClassA.sql5")
        assertThat(ClassA().sql6 {}).isEqualTo("com.example.core.ClassA.sql6")
    }

    @Test
    fun `id resolved through a delegating wrapper points at the wrapper`() {
        // A decorator forwarding to the client (KueryBlockingClient by delegate, guard wrappers,
        // ...) contributes the first non-kuery frame, so the auto-resolved sqlId identifies the
        // wrapper method, NOT the repository call site. Wrapped clients should pass an explicit
        // sqlId via sql(sqlId, block) if per-call-site ids are needed.
        assertThat(com.example.core.RepositoryBehindDelegation().find())
            .isEqualTo("com.example.core.DelegatingClient.sql")
    }

    @Test
    fun `removeSuffixes strips a matching suffix and leaves the string unchanged otherwise`() {
        assertThat("a.b.c".removeSuffixes(listOf(".b", ".c"))).isEqualTo("a.b")
        assertThat("a.b.c".removeSuffixes(listOf(".a", ".d"))).isEqualTo("a.b.c")
    }

    @Test
    fun `id does not pin the class loader of the block class`() {
        // when
        val loaderRef = useBlockFromIsolatedClassLoader()

        // then
        repeat(100) {
            if (loaderRef.get() == null) {
                return
            }
            System.gc()
            Thread.sleep(10)
        }
        assertThat(loaderRef.get()).isNull()
    }

    private fun useBlockFromIsolatedClassLoader(): WeakReference<ClassLoader> {
        val loader = ChildFirstClassLoader(BLOCK_CLASS_NAME, checkNotNull(javaClass.classLoader))
        val blockClass = Class.forName(BLOCK_CLASS_NAME, true, loader)
        check(blockClass.classLoader === loader)

        @Suppress("UNCHECKED_CAST")
        val block = blockClass.getDeclaredConstructor().newInstance() as SqlBuilder.() -> Unit
        block.id()
        return WeakReference(loader)
    }

    /**
     * Loads only [targetClassName] by itself so that the class gets its own class loader,
     * while delegating everything else to the parent.
     */
    private class ChildFirstClassLoader(
        private val targetClassName: String,
        private val parentLoader: ClassLoader,
    ) : ClassLoader(parentLoader) {
        override fun loadClass(
            name: String,
            resolve: Boolean,
        ): Class<*> {
            if (name != targetClassName) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                val resource = name.replace('.', '/') + ".class"
                val bytes = checkNotNull(parentLoader.getResourceAsStream(resource)).use { it.readBytes() }
                val clazz = defineClass(name, bytes, 0, bytes.size)
                if (resolve) {
                    resolveClass(clazz)
                }
                return clazz
            }
        }
    }

    companion object {
        private const val BLOCK_CLASS_NAME = "com.example.core.IsolatedBlock"
    }
}
