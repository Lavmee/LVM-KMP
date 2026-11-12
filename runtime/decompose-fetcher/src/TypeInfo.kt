package tech.annexflow.lvm.decompose.fetcher

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import io.ktor.util.reflect.TypeInfo as KtorTypeInfo

/** The type of a value to read from a [ResponseBody]: its class, and its full type when it is known. */
public class TypeInfo(
    public val type: KClass<*>,
    public val kotlinType: KType? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeInfo) return false
        return if (kotlinType != null || other.kotlinType != null) {
            kotlinType == other.kotlinType
        } else {
            type == other.type
        }
    }

    override fun hashCode(): Int = kotlinType?.hashCode() ?: type.hashCode()

    override fun toString(): String = "TypeInfo(${kotlinType ?: type})"
}

/** The [TypeInfo] of [T]. */
public inline fun <reified T> typeInfo(): TypeInfo = TypeInfo(type = T::class, kotlinType = typeOfOrNull<T>())

internal fun TypeInfo.asKtorTypeInfo(): KtorTypeInfo = KtorTypeInfo(type = type, kotlinType = kotlinType)

// typeOf can fail at run time, for example when R8 removed the signatures of a class. The class alone still works, so
// the failure is dropped on purpose.
@PublishedApi
internal inline fun <reified T> typeOfOrNull(): KType? = try {
    typeOf<T>()
} catch (
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
) {
    null
}
