package game.vinto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.art.Res
import game.vinto.app.art.online_body
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Apostrophes are apostrophes, not backslashes.
 *
 * Android's string format escapes an apostrophe as `\'` and every Android developer types it
 * out of habit — but these are compose-resources strings, and its loader does not process that
 * escape. The backslash goes to the screen: *"the coalition\'s hand"*, in the middle of the
 * one sentence that explains the final round. Three strings had it before this test existed,
 * one of them on a screen shipped for months.
 *
 * The rendered half of the test is what makes the ban precise rather than superstitious: `\n`
 * *is* processed, so the rule is about quote escapes specifically and not about backslashes.
 */
@OptIn(ExperimentalTestApi::class)
class StringEscapeTest {

    @Test
    fun noStringEscapesItsQuotes() {
        val offenders = stringFiles().flatMap { xml ->
            xml.readLines().withIndex()
                .filter { (_, line) -> BANNED.any { it in line } }
                .map { (i, line) -> "${xml.parentFile.name}/strings.xml:${i + 1} $line" }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "compose-resources does not process \\' or \\\" — the backslash is drawn:\n" +
                    offenders.joinToString("\n"),
            )
        }
    }

    @Test
    fun aLineBreakStillMeansALineBreak() = runComposeUiTest {
        val read = mutableStateOf("")
        setContent { read.value = stringResource(Res.string.online_body) }
        waitForIdle()

        val body by read
        assertTrue("\n" in body, "the escape the loader does handle stopped being handled")
        assertTrue("\\" !in body, "a backslash reached the screen: $body")
    }

    private fun stringFiles(): List<File> =
        resources().listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .also { assertTrue(it.isNotEmpty(), "no strings found to check") }

    private fun resources(): File {
        var here: File? = File(System.getProperty("user.dir"))
        while (here != null) {
            val candidate = File(here, "composeApp/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            val inside = File(here, "src/commonMain/composeResources")
            if (inside.isDirectory) return inside
            here = here.parentFile
        }
        fail("cannot find composeResources from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val BANNED = listOf("""\'""", """\"""")
    }
}
