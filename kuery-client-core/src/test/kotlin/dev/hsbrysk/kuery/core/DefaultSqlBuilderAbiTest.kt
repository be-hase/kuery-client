package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * `DefaultSqlBuilder.interpolate` is effectively public ABI even though the class is internal:
 * the compiler plugin rewrites string interpolation in user code into a `CHECKCAST` to this class
 * followed by an `INVOKEVIRTUAL` of this exact method, so every compiled user artifact links
 * against it. Because the class is internal, it is outside the scope of the Kotlin ABI validation
 * that guards the public API. This test pins the JVM-level contract instead.
 *
 * If this test fails, you are about to break binary compatibility with every artifact compiled
 * by an older version of the compiler plugin (`NoSuchMethodError` / `ClassNotFoundException` at
 * runtime). Do not rename, move, or change the signature of `interpolate`.
 */
class DefaultSqlBuilderAbiTest {
    @Test
    fun `interpolate signature is frozen`() {
        // Intentionally string literals (not ::class / type references) so that IDE refactorings
        // cannot silently update this test along with the production code.
        // Moved from `dev.hsbrysk.kuery.core.internal` before v1.0.0 (sealing SqlBuilder requires
        // the implementation to be in the same package). Frozen at this location from 1.0 onward.
        val clazz = Class.forName("dev.hsbrysk.kuery.core.DefaultSqlBuilder")
        // Throws NoSuchMethodException if the name or parameter types change.
        val method = clazz.getMethod("interpolate", List::class.java, List::class.java)

        // CHECKCAST + INVOKEVIRTUAL require the class and method to be public at the JVM level.
        assertThat(Modifier.isPublic(clazz.modifiers)).isTrue()
        assertThat(Modifier.isPublic(method.modifiers)).isTrue()
        assertThat(Modifier.isStatic(method.modifiers)).isFalse()
        assertThat(method.returnType).isEqualTo(String::class.java)
    }
}
