package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No `.gradle.kts` file ends inside a block comment.
 *
 * **Kotlin block comments nest.** A `/*` anywhere inside a KDoc — including inside a path or a
 * glob written in prose — opens a second comment, and the `*/` that looks like it closes the KDoc
 * closes only that inner one. The outer comment then runs to the end of the file.
 *
 * In a `.kt` source that shows up as missing declarations. In a **build script** it is silent: the
 * build succeeds, the swallowed configuration simply never runs. `composeApp/build.gradle.kts`
 * carried this for a while — a KDoc mentioned `reports/` immediately followed by `*-composables`,
 * and everything after it, `composeCompiler { }` included, was comment. What made it expensive is
 * how it presents: a `logger.lifecycle` at the end of the file prints nothing, a `tasks.register`
 * there leaves a task Gradle reports as "not found in project", and appended code produces no
 * error of any kind. It was first misdiagnosed as a rule about `composeCompiler { }`.
 *
 * Scoped to build scripts because that is where the failure is silent, and because they are the
 * files somebody appends to without recompiling anything.
 */
class BuildScriptCommentsTest {

    @Test
    fun noBuildScriptEndsInsideABlockComment() {
        val scripts = repoRoot().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .toList()

        // If the walk found nothing, the test would pass while checking nothing at all.
        assertTrue(scripts.size >= 5, "only found ${scripts.size} build scripts — the walk is wrong")

        val unbalanced = scripts.filter { openBlockDepthAtEnd(it.readText()) > 0 }
            .map { it.relativeTo(repoRoot()).path }

        assertEquals(
            emptyList(),
            unbalanced,
            "these build scripts end inside a block comment, so everything after that point is " +
                "silently not configuration: $unbalanced",
        )
    }

    /**
     * The scanner notices the real thing, on the real line that caused it.
     *
     * Without this the test above could pass by never detecting anything.
     */
    @Test
    fun theScannerNoticesAGlobThatOpensAComment() {
        // Built line by line rather than as a raw string: a raw string holding `/*` and `*/`
        // is exactly the shape this test is about, and ktlint's indentation rule for one
        // disagrees with its own autocorrect.
        val swallowed = listOf(
            "/**",
            " * A doc comment.",
            " *",
            " *     # then read build/compose/reports/*-composables.txt",
            " */",
            "composeCompiler { }",
        ).joinToString("\n")

        assertEquals(1, openBlockDepthAtEnd(swallowed), "missed the nested opener")
        assertEquals(0, openBlockDepthAtEnd(swallowed.replace("reports/*-", "reports/ *-")), "false positive")
    }

    /** Block-comment nesting depth at end of text, skipping strings and line comments. */
    private fun openBlockDepthAtEnd(text: String): Int {
        var i = 0
        var depth = 0
        while (i < text.length) {
            if (depth == 0 && !text.startsWith("/*", i)) {
                i = skipCode(text, i)
                continue
            }
            when {
                text.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }
                text.startsWith("*/", i) -> {
                    depth--
                    i += 2
                }
                else -> {
                    i++
                }
            }
        }
        return depth
    }

    /** Advance one token outside any comment, stepping whole over literals and line comments. */
    private fun skipCode(text: String, i: Int): Int = when {
        text.startsWith("//", i) -> text.indexOf('\n', i).let { if (it == -1) text.length else it }
        text.startsWith("\"\"\"", i) -> text.indexOf("\"\"\"", i + 3).let { if (it == -1) text.length else it + 3 }
        text[i] == '"' -> endOfString(text, i)
        else -> i + 1
    }

    private fun endOfString(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                '\n' -> return i // an unterminated literal is not this test's problem
                else -> i++
            }
        }
        return i
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("no settings.gradle.kts above ${System.getProperty("user.dir")}")
    }
}
