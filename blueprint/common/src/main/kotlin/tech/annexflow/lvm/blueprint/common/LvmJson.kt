package tech.annexflow.lvm.blueprint.common

/**
 * Writes maps, lists, strings, integers, booleans and `null` as JSON: two spaces per level, map entries in their
 * iteration order and a line break at the end. The report needs no more than this, so blueprint adds no JSON library
 * to the consumer's build classpath.
 */
internal object LvmJson {

    fun write(value: Any?): String = StringBuilder().apply { appendValue(value, 0) }.append('\n').toString()

    private fun StringBuilder.appendValue(value: Any?, depth: Int) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean, is Int, is Long -> append(value.toString())
            is Map<*, *> -> appendContainer('{', '}', value.entries, depth) { entry ->
                appendString(entry.key.toString())
                append(": ")
                appendValue(entry.value, depth + 1)
            }
            is Iterable<*> -> appendContainer('[', ']', value.toList(), depth) { appendValue(it, depth + 1) }
            else -> throw IllegalArgumentException("Can't write ${value.javaClass.name} as JSON.")
        }
    }

    private fun <T> StringBuilder.appendContainer(
        open: Char,
        close: Char,
        items: Collection<T>,
        depth: Int,
        appendItem: StringBuilder.(T) -> Unit,
    ) {
        append(open)
        if (items.isEmpty()) {
            append(close)
            return
        }
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('\n')
            indent(depth + 1)
            appendItem(item)
        }
        append('\n')
        indent(depth)
        append(close)
    }

    private fun StringBuilder.indent(depth: Int) {
        repeat(depth) { append("  ") }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append("\\u").append(char.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
                else -> append(char)
            }
        }
        append('"')
    }

    private const val HEX = 16
    private const val UNICODE_ESCAPE_DIGITS = 4
}
