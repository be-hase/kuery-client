package dev.hsbrysk.kuery.core

@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Kuery Client API." +
        " It may be changed or removed without notice, and no compatibility is guaranteed between versions.",
)
public annotation class KueryClientInternalApi
