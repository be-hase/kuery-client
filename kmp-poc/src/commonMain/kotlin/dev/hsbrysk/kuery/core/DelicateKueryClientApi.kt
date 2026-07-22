package dev.hsbrysk.kuery.core

/**
 * Marks declarations that bypass the safety mechanisms of Kuery Client (e.g.
 * [SqlBuilder.addUnsafe], which skips the compiler plugin's automatic parameter binding) and can
 * introduce SQL injection when misused.
 *
 * Such APIs are still needed for dynamic SQL that the compiler plugin cannot handle, but make
 * sure you fully read and understand the documentation of the declaration before opting in.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is a delicate API and its use requires care." +
        " Make sure you fully read and understand documentation of the declaration that is marked as a delicate API.",
)
public annotation class DelicateKueryClientApi
