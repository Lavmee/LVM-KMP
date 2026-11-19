package tech.annexflow.lvm.blueprint.maincontext

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import tech.annexflow.lvm.blueprint.common.LvmExtension
import tech.annexflow.lvm.blueprint.common.requireSection

/** `lvm { mainContext { } }` */
abstract class MainContextSection {
    /**
     * Platforms whose compilations run the compiler plugin: any of `android`, `ios`, `jvm`. An empty list turns the
     * plugin off. Replaces the build-wide `lvm.mainContext.platforms`.
     */
    abstract val platforms: ListProperty<String>

    /**
     * Whether the test compilations of [platforms] run the compiler plugin too. With `false`, only main code does: unit
     * tests, instrumented tests, test fixtures and other compilations, such as the one of `src@integrationTest`, compile
     * without it, so a test can call a `@MainContext` function from `runTest` without a main dispatcher. Calls in main
     * code still move to the main dispatcher, so a test of code that makes such calls still needs `Dispatchers.setMain`.
     */
    abstract val testCompilations: Property<Boolean>

    /**
     * Adds `lvm-main-context`, with the `@MainContext` annotation, to `commonMain`, or to `implementation` in a module
     * without Kotlin Multiplatform.
     */
    abstract val dependency: Property<Boolean>
}

/** The module's `lvm { mainContext { } }`, which `tech.annexflow.lvm.main-context` adds. */
val LvmExtension.mainContext: MainContextSection
    get() = requireSection(MainContextSection::class.java, "mainContext", "tech.annexflow.lvm.main-context")

fun LvmExtension.mainContext(action: Action<MainContextSection>) = action.execute(mainContext)
