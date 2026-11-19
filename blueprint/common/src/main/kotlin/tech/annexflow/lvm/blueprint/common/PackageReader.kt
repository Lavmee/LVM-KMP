package tech.annexflow.lvm.blueprint.common

/**
 * Reads the package that a Kotlin or a Java file declares, without the compiler: the name after the first `package`,
 * past a byte order mark, a shebang line, comments and annotations, such as Kotlin's `@file:JvmName("Names")` or those
 * of a Java `package-info.java`. Backticks around a segment are dropped.
 *
 * [nestedComments] is on for Kotlin, whose block comments nest, and off for Java, whose block comments don't.
 */
internal class PackageReader(private val source: String, private val nestedComments: Boolean) {

    private var position = 0

    /** The declared package, such as `com.example.feature`, or `null` when the file declares none. */
    fun read(): String? {
        if (peek() == BYTE_ORDER_MARK) position++
        if (source.startsWith("#!", position)) skipLine()
        while (true) {
            skipTrivia()
            when {
                peek() == '@' -> skipAnnotation()
                atKeyword("package") -> {
                    position += "package".length
                    skipTrivia()
                    return qualifiedName()?.joinToString(".")
                }
                else -> return null
            }
        }
    }

    private fun peek(offset: Int = 0): Char? = source.getOrNull(position + offset)

    private fun atKeyword(keyword: String): Boolean =
        source.startsWith(keyword, position) && peek(keyword.length)?.let(Character::isJavaIdentifierPart) != true

    /** Skips whitespace and comments. */
    private fun skipTrivia() {
        while (true) {
            when {
                peek()?.isWhitespace() == true -> position++
                source.startsWith("//", position) -> skipLine()
                source.startsWith("/*", position) -> skipBlockComment()
                else -> return
            }
        }
    }

    private fun skipLine() {
        while (peek() != null && peek() != '\n') position++
    }

    private fun skipBlockComment() {
        position += 2
        var depth = 1
        while (depth > 0 && peek() != null) {
            when {
                source.startsWith("*/", position) -> {
                    depth--
                    position += 2
                }
                nestedComments && source.startsWith("/*", position) -> {
                    depth++
                    position += 2
                }
                else -> position++
            }
        }
    }

    /**
     * Skips `@Name`, `@a.b.Name(arguments)`, Kotlin's `@file:Name(arguments)` and `@file:[A B(arguments)]`. Type
     * arguments, as in `@file:Annotation<T>`, aren't skipped, so the file reads as one without a package.
     */
    private fun skipAnnotation() {
        position++
        val name = qualifiedName()
        skipTrivia()
        if (peek() == ':' && name == listOf("file")) {
            position++
            skipTrivia()
            if (peek() == '[') {
                skipBalanced('[', ']')
                return
            }
            qualifiedName()
            skipTrivia()
        }
        if (peek() == '(') skipBalanced('(', ')')
    }

    /** Skips from [open] to its matching [close], past strings, characters and comments that may hold either. */
    private fun skipBalanced(open: Char, close: Char) {
        var depth = 0
        while (true) {
            when {
                peek() == null -> return
                source.startsWith("//", position) -> skipLine()
                source.startsWith("/*", position) -> skipBlockComment()
                source.startsWith("\"\"\"", position) -> skipRawString()
                peek() == '"' || peek() == '\'' -> skipQuoted(peek()!!)
                peek() == open -> {
                    depth++
                    position++
                }
                peek() == close -> {
                    position++
                    if (--depth == 0) return
                }
                else -> position++
            }
        }
    }

    private fun skipRawString() {
        val end = source.indexOf("\"\"\"", position + 3)
        position = if (end < 0) source.length else end + 3
        // A raw string may end in more quotes than three, as in """a"""".
        while (peek() == '"') position++
    }

    private fun skipQuoted(quote: Char) {
        position++
        while (true) {
            when (peek()) {
                null, '\n' -> return
                '\\' -> position += 2
                quote -> {
                    position++
                    return
                }
                else -> position++
            }
        }
    }

    /**
     * Reads identifiers separated by dots, with whitespace and comments around the dots, as Kotlin allows, and stops
     * before anything else, such as `;` or the next line's `import`. `null` when no identifier comes first.
     */
    private fun qualifiedName(): List<String>? {
        val segments = mutableListOf(identifier() ?: return null)
        while (true) {
            val beforeDot = position
            skipTrivia()
            if (peek() != '.') {
                position = beforeDot
                return segments
            }
            position++
            skipTrivia()
            val segment = identifier()
            if (segment == null) {
                position = beforeDot
                return segments
            }
            segments += segment
        }
    }

    private fun identifier(): String? {
        if (peek() == '`') {
            val end = source.indexOf('`', position + 1)
            if (end < 0 || source.substring(position + 1, end).contains('\n')) return null
            return source.substring(position + 1, end).also { position = end + 1 }
        }
        if (peek()?.let(Character::isJavaIdentifierStart) != true) return null
        val start = position
        while (peek()?.let(Character::isJavaIdentifierPart) == true) position++
        return source.substring(start, position)
    }

    private companion object {
        const val BYTE_ORDER_MARK = '﻿'
    }
}
