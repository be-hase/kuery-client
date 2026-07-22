package dev.hsbrysk.kuery.core

/**
 * Marks declarations that are internal to Kuery Client even though they are `public` for
 * technical reasons (e.g. they are referenced by other Kuery Client modules or by code the
 * compiler plugin generates).
 *
 * They may be changed or removed without notice, are excluded from the ABI compatibility
 * checks, and must not be used outside of Kuery Client itself.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Kuery Client API." +
        " It may be changed or removed without notice, and no compatibility is guaranteed between versions.",
)
public annotation class KueryClientInternalApi
