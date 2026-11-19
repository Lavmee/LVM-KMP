package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceLayoutCheckTest {

    /** A file at [path] in the module, whose first segment is its code root, declaring [declaredPackage]. */
    private fun file(path: String, declaredPackage: String?): SourceFile {
        val segments = path.split('/')
        return SourceFile(segments.first(), segments.drop(1).dropLast(1), segments.last(), declaredPackage)
    }

    private fun misplaced(vararg files: SourceFile): Map<String, String> =
        SourceLayoutCheck(files.toList()).misplaced.associate { it.file.path to it.expectedPackage }

    @Test
    fun `the root package is the longest package that the files of every code root share`() {
        val check = SourceLayoutCheck(
            listOf(
                file("src/Chat.kt", "com.example.chat"),
                file("src@jvm/ui/Window.kt", "com.example.chat.ui"),
                file("test/data/RepositoryTest.kt", "com.example.chat.data"),
            ),
        )

        assertEquals(listOf("com", "example", "chat"), check.rootPackage)
        assertEquals(emptyList(), check.misplaced)
        assertNull(check.failure(":chat"))
    }

    @Test
    fun `the root package ends at a whole segment`() {
        val check = SourceLayoutCheck(listOf(file("src/a/A.kt", "com.example.chat"), file("src/b/B.kt", "com.example.chatbot")))

        assertEquals(listOf("com", "example"), check.rootPackage)
    }

    @Test
    fun `a file in the directory of another subpackage expects the package of its directory`() {
        val wrong = misplaced(
            file("src/Chat.kt", "com.example.chat"),
            file("src/ui/Screen.kt", "com.example.chat.ui"),
            file("src/ui/Model.kt", "com.example.chat.data"),
        )

        assertEquals(mapOf("src/ui/Model.kt" to "com.example.chat.ui"), wrong)
    }

    @Test
    fun `directories that repeat the root package are misplaced`() {
        val check = SourceLayoutCheck(
            listOf(file("src/Chat.kt", "com.example.chat"), file("src/com/example/chat/ui/Screen.kt", "com.example.chat.ui")),
        )

        val wrong = check.misplaced.single()
        assertEquals("src/com/example/chat/ui/Screen.kt", wrong.file.path)
        assertTrue(wrong.repeatsRootPackage)
    }

    @Test
    fun `a module whose directories all spell out their packages has its whole path repeated`() {
        val wrong = misplaced(
            file("src/com/example/chat/Chat.kt", "com.example.chat"),
            file("src/com/example/chat/ui/Screen.kt", "com.example.chat.ui"),
        )

        assertEquals(setOf("src/com/example/chat/Chat.kt", "src/com/example/chat/ui/Screen.kt"), wrong.keys)
    }

    @Test
    fun `a file without a package is misplaced and doesn't shorten the root package`() {
        val check = SourceLayoutCheck(
            listOf(file("src/Chat.kt", "com.example.chat"), file("src/ui/Screen.kt", "com.example.chat.ui"), file("src/Util.kt", null)),
        )

        assertEquals(listOf("com", "example", "chat"), check.rootPackage)
        assertEquals(mapOf("src/Util.kt" to "com.example.chat"), check.misplaced.associate { it.file.path to it.expectedPackage })
        assertNotNull(check.failure(":chat"))
    }

    @Test
    fun `a file whose package is its directory alone is named as the reason for a missing root package`() {
        val check = SourceLayoutCheck(
            listOf(
                file("src/Chat.kt", "com.example.chat"),
                file("src/data/Repository.kt", "com.example.chat.data"),
                file("src/ui/Screen.kt", "ui"),
            ),
        )

        assertEquals(emptyList(), check.rootPackage)
        assertEquals(setOf("src/Chat.kt", "src/data/Repository.kt"), check.misplaced.map { it.file.path }.toSet())
        assertEquals(listOf("src/ui/Screen.kt"), check.withoutRootPackage.map(SourceFile::path))
    }

    @Test
    fun `a module without a root package passes while every directory spells out its package`() {
        val check = SourceLayoutCheck(listOf(file("src/chat/Chat.kt", "chat"), file("src/data/Repository.kt", "data")))

        assertNull(check.failure(":chat"))
    }
}
