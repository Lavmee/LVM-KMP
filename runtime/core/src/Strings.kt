package tech.annexflow.lvm.core

/** Replaces the last occurrence of [oldChar] with [newChar], or returns this string if there is none. */
public fun String.replaceLast(oldChar: Char, newChar: Char, ignoreCase: Boolean = false): String {
    val index = lastIndexOf(oldChar, ignoreCase = ignoreCase)
    return if (index < 0) this else this.replaceRange(index, index + 1, newChar.toString())
}

/** Replaces the last occurrence of [oldValue] with [newValue], or returns this string if there is none. */
public fun String.replaceLast(oldValue: String, newValue: String, ignoreCase: Boolean = false): String {
    val index = lastIndexOf(oldValue, ignoreCase = ignoreCase)
    return if (index < 0) this else this.replaceRange(index, index + oldValue.length, newValue)
}
