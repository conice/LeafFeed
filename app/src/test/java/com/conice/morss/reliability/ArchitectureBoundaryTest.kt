package com.conice.morss.reliability

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
                    "import com.conice.morss.application.",
                    "import com.conice.morss.infrastructure.",
                    "import com.conice.morss.ui.",
                ),
        )
    }

    @Test
    fun `application does not depend on ui`() {
        assertNoImports(
            sourceDirectory("application"),
            forbiddenPrefixes = listOf("import com.conice.morss.ui."),
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
        val fromRoot = File("app/src/main/java/com/conice/morss/$name")
        if (fromRoot.isDirectory) return fromRoot
        return File("src/main/java/com/conice/morss/$name").also {
            check(it.isDirectory) { "Unable to locate the $name source directory" }
        }
    }
}
