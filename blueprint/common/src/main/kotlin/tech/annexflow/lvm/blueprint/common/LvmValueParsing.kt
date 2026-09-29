package tech.annexflow.lvm.blueprint.common

import org.gradle.api.InvalidUserDataException

/** Parses the string form of build-wide values: Gradle properties and values forwarded from settings. */
object LvmValueParsing {

    fun int(key: String, raw: String): Int =
        raw.trim().toIntOrNull()
            ?: throw InvalidUserDataException("${LvmKeys.property(key)} must be an integer, but was '$raw'.")

    fun boolean(key: String, raw: String): Boolean =
        when (raw.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw InvalidUserDataException("${LvmKeys.property(key)} must be true or false, but was '$raw'.")
        }

    /** Comma-separated list; blank entries are dropped. */
    fun list(raw: String): List<String> = raw.split(',').map(String::trim).filter(String::isNotEmpty)

    fun joinList(values: List<String>): String = values.joinToString(",")
}
