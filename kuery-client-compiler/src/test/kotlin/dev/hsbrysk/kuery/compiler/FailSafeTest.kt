package dev.hsbrysk.kuery.compiler

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.compiler.fir.failSafe
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Test

class FailSafeTest {
    @Test
    fun `failSafe rethrows compiler cancellation instead of failing open`() {
        // ProcessCanceledException is how the compiler/IDE aborts an analysis in progress —
        // control flow, not a checker failure, so it must keep propagating.
        assertFailure {
            failSafe { throw ProcessCanceledException() }
        }.isInstanceOf(ProcessCanceledException::class)
    }

    @Test
    fun `failSafe fails open on an ordinary checker exception`() {
        // Must not throw: a checker bug costs the warning, not the user's build.
        failSafe { error("checker bug") }
    }
}
