@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.data.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import org.springframework.core.KotlinDetector
import org.springframework.core.convert.ConversionService
import org.springframework.data.convert.CustomConversions
import java.lang.reflect.Array as ReflectArray

/** Shared conversion of collection and object-array bind values for Spring Data clients. */
@KueryClientInternalApi
public class SpringDataWriteConverter(
    private val conversionService: ConversionService,
    private val customConversions: CustomConversions,
) {
    public fun convertCollection(collection: Collection<*>): Collection<*> = collection.map { convertElement(it) }

    // The runtime component type must be preserved (e.g. String[] stays String[]); drivers resolve
    // the SQL array type from it, and PostgreSQL drivers reject Object[] outright.
    public fun convertArray(array: Array<*>): Array<*> {
        val converted = array.map { convertElement(it) }
        val targetType = componentWriteTarget(array.javaClass.componentType)
        if (targetType == null && array.indices.all { converted[it] === array[it] }) {
            return array
        }
        val inferredType = converted.mapNotNull { it?.javaClass }.distinct().singleOrNull() ?: Any::class.java
        val componentType = targetType ?: inferredType
        val result = ReflectArray.newInstance(componentType, array.size)
        converted.forEachIndexed { index, value -> ReflectArray.set(result, index, value) }
        @Suppress("UNCHECKED_CAST")
        return result as Array<*>
    }

    // The component type itself carries the conversion intent even when the elements do not
    // (all-null or empty arrays), e.g. SampleEnum[] must become String[] regardless of contents.
    public fun componentWriteTarget(componentType: Class<*>): Class<*>? {
        val targetType = customConversions.getCustomWriteTarget(componentType)
        return when {
            targetType.isPresent -> targetType.get()
            KotlinDetector.isInlineClass(componentType) -> {
                val underlying = ValueClasses.underlyingType(componentType)
                // A generic value class erases its underlying to Object, which is not a useful SQL
                // array component type. Infer it from the converted elements when possible.
                if (underlying == Any::class.java) null else componentWriteTarget(underlying) ?: underlying
            }
            Enum::class.java.isAssignableFrom(componentType) -> String::class.java
            else -> null
        }
    }

    private fun convertElement(element: Any?): Any? {
        if (element == null) {
            return null
        }
        val targetType = customConversions.getCustomWriteTarget(element::class.java)
        return when {
            targetType.isPresent -> conversionService.convert(element, targetType.get())
            KotlinDetector.isInlineClass(element.javaClass) -> convertElement(ValueClasses.unbox(element))
            // Composite IN `(a, b) IN ($pairs)` passes each row as an Object[] entry in a Collection.
            element is Array<*> -> convertArray(element)
            element is Enum<*> -> element.name
            else -> element
        }
    }
}
