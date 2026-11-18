package tech.annexflow.lvm.blueprint.common

import groovy.json.JsonSlurper
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `lvm-keys.json` describes the build-wide values for tools that read a build without running Gradle, such as lvm-tui.
 * These tests keep it in step with [LvmKeys], [LvmDefaults] and the minimum versions.
 */
class LvmKeysFileTest {

    @Suppress("UNCHECKED_CAST")
    private val file = JsonSlurper().parse(File("lvm-keys.json")) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private val entries: Map<String, Map<String, Any?>> =
        (file.getValue("keys") as List<Map<String, Any?>>).associateBy { it.getValue("key") as String }

    @Test
    fun `lists every key of LvmKeys and no other`() {
        assertEquals(keyConstants().values.toSortedSet(), entries.keys.toSortedSet())
    }

    @Test
    fun `defaults and types match LvmDefaults`() {
        val defaults = defaultConstants()
        for ((constant, key) in keyConstants()) {
            val entry = entries.getValue(key)
            val default = defaults[constant]
            assertEquals(default, entry["default"], "default of $key")
            if (default != null) assertEquals(typeName(default), entry["type"], "type of $key")
        }
    }

    @Test
    fun `lists say how a module combines them and other values do not`() {
        for ((key, entry) in entries) {
            val isList = entry["type"] == "list"
            if (isList) {
                assertEquals(true, entry["module"] in setOf("append", "replace"), "module of $key")
            } else {
                assertNull(entry["module"], "module of $key")
            }
        }
    }

    @Test
    fun `minimum versions match the ones blueprint enforces`() {
        val expected = mapOf("gradle" to LvmBuildInfo.MIN_GRADLE, "kotlin" to LvmBuildInfo.MIN_KOTLIN, "agp" to LvmBuildInfo.MIN_AGP)
        assertEquals(expected, file["minimums"])
    }

    /** Constant name -> key, for every key in [LvmKeys]. */
    private fun keyConstants(): Map<String, String> =
        LvmKeys::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name != "PREFIX" }
            .associate { it.name to it.get(null) as String }

    /** Constant name -> default, for every default in [LvmDefaults]. */
    private fun defaultConstants(): Map<String, Any?> =
        LvmDefaults::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name != "INSTANCE" }
            .associate { field ->
                field.isAccessible = true
                field.name to field.get(null)
            }

    private fun typeName(value: Any): String = when (value) {
        is Int -> "int"
        is Boolean -> "boolean"
        is String -> "string"
        is List<*> -> "list"
        else -> error("Unexpected default type ${value.javaClass.name}")
    }
}
