package me.ash.reader.reliability

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `domain does not depend on application infrastructure or ui`() {
        assertNoImports(
            sourceDirectory("domain"),
            forbiddenPrefixes =
                listOf(
                    "import me.ash.reader.application.",
                    "import me.ash.reader.infrastructure.",
                    "import me.ash.reader.ui.",
                ),
        )
    }

    @Test
    fun `application does not depend on ui`() {
        assertNoImports(
            sourceDirectory("application"),
            forbiddenPrefixes = listOf("import me.ash.reader.ui."),
        )
    }

    private fun assertNoImports(directory: File, forbiddenPrefixes: List<String>) {
        val violations =
            directory
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.useLines { lines ->
                        lines
                            .filter { line -> forbiddenPrefixes.any(line::startsWith) }
                            .map { line -> "${file.relativeTo(directory)}: $line" }
                            .toList()
                            .asSequence()
                    }
                }
                .toList()
        assertTrue("Architecture boundary violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun sourceDirectory(name: String): File {
        val fromRoot = File("app/src/main/java/me/ash/reader/$name")
        if (fromRoot.isDirectory) return fromRoot
        return File("src/main/java/me/ash/reader/$name").also {
            check(it.isDirectory) { "Unable to locate the $name source directory" }
        }
    }
}
