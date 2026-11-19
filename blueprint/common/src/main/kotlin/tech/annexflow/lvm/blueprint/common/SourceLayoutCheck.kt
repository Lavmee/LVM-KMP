package tech.annexflow.lvm.blueprint.common

/** A Kotlin or Java file in a code root of the flat layout. */
internal class SourceFile(
    /** The code root, such as `src` or `test@jvm`, relative to the module. */
    val root: String,
    /** The directories between the code root and the file, such as `ui` and `chat` for `src/ui/chat/Screen.kt`. */
    val directories: List<String>,
    val name: String,
    /** The package the file declares, `null` for none. */
    val declaredPackage: String?,
) {
    /** The file's path in the module. */
    val path: String
        get() = (listOf(root) + directories + name).joinToString("/")
}

/** A file outside the directory of its package. */
internal class MisplacedFile(
    val file: SourceFile,
    /** The package that the file's directory stands for. */
    val expectedPackage: String,
    /** Whether the file's directories start with the root package, as in `src/com/example/feature/` for `com.example.feature`. */
    val repeatsRootPackage: Boolean,
)

/**
 * Where the files of a module with the flat layout sit against the packages they declare.
 *
 * The module's root package is the longest package prefix that every file with a package declaration shares. A file
 * in `<code root>/a/b/` declares `<root package>.a.b`, and one directly in its code root declares the root package
 * itself, so the directories never repeat the root package. A file without a package declaration is misplaced too.
 */
internal class SourceLayoutCheck(files: List<SourceFile>) {

    private val sortedFiles = files.sortedBy(SourceFile::path)

    /** The root package by segment; empty when the files share none. */
    val rootPackage: List<String> = sortedFiles.mapNotNull { it.declaredPackage?.split('.') }.reduceOrNull(::commonPrefix).orEmpty()

    /** The packages that the files declare, sorted. */
    val packages: List<String> = sortedFiles.mapNotNull(SourceFile::declaredPackage).distinct().sorted()

    val misplaced: List<MisplacedFile> = sortedFiles.mapNotNull { file ->
        val expected = (rootPackage + file.directories).joinToString(".")
        if (file.declaredPackage == expected) return@mapNotNull null
        val repeats = rootPackage.isNotEmpty() && file.directories.take(rootPackage.size) == rootPackage
        MisplacedFile(file, expected, repeats)
    }

    /**
     * Files that declare the package of their directories alone, such as `ui` for `src/ui/Screen.kt`, as IDE templates
     * do when they don't know the root package. With one of them, the files share no root package, and every other file
     * looks misplaced.
     */
    val withoutRootPackage: List<SourceFile> =
        if (rootPackage.isNotEmpty() || misplaced.isEmpty()) {
            emptyList()
        } else {
            sortedFiles.filter { it.directories.isNotEmpty() && it.declaredPackage == it.directories.joinToString(".") }
        }

    /** The error for [modulePath] that names every misplaced file, or `null` when there is none. */
    fun failure(modulePath: String): String? {
        if (misplaced.isEmpty()) return null
        val root = rootPackage.joinToString(".")
        return buildString {
            append("$modulePath has Kotlin or Java files outside the directory of their package. With the flat layout ")
            append("(lvm.kotlin.flatLayout), a file in <code root>/a/b/ declares <root package>.a.b, and a file directly ")
            append("in its code root declares the root package itself:\n")
            for (wrong in misplaced) {
                append("  ${wrong.file.path}: declares ${wrong.file.declaredPackage ?: "no package"}")
                when {
                    wrong.expectedPackage.isNotEmpty() -> append(", its directory expects ${wrong.expectedPackage}")
                    wrong.file.declaredPackage != null -> append(", its directory expects no package")
                }
                if (wrong.repeatsRootPackage) {
                    val directory = (listOf(wrong.file.root) + wrong.file.directories.drop(rootPackage.size)).joinToString("/")
                    append("; the directories repeat the root package, so move it to $directory/")
                }
                append('\n')
            }
            if (root.isEmpty()) {
                append("The files share no package, so the module has no root package.")
            } else {
                append("The root package is $root, the longest package that the module's files share.")
            }
            append(" Packages of the module: ${packages.joinToString().ifEmpty { "none" }}.\n")
            if (withoutRootPackage.isNotEmpty()) {
                append("These files declare only the package of their directory, as IDE templates do, which leaves out the ")
                append("root package: ${withoutRootPackage.joinToString { "${it.path} (${it.declaredPackage})" }}. ")
                append("Add the root package to their package declarations.\n")
            }
            append("Move each file to the directory of its package or change its package declaration, or turn the check ")
            append("off with lvm { kotlin { checkSourceLayout = false } } in the module or lvm.kotlin.checkSourceLayout=false.")
        }
    }
}

private fun commonPrefix(a: List<String>, b: List<String>): List<String> = a.zip(b).takeWhile { (x, y) -> x == y }.map { it.first }
