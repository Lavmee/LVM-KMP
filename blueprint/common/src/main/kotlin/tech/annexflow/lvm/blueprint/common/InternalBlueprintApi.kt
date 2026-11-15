package tech.annexflow.lvm.blueprint.common

/**
 * Marks declarations that LVM's blueprint plugins share among themselves. They are not part of blueprint's supported API
 * and may change in any release. Build logic reads build-wide values with [lvmValues] and reaches a module's sections
 * with the accessors of `lvm { }`, such as `lvm.android`.
 */
@RequiresOptIn(
    message = "Internal to LVM's blueprint plugins, and may change in any release. Use lvmValues and the lvm { } accessors.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class InternalBlueprintApi
