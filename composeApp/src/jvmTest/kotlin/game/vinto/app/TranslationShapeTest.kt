package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every translation says the same *shape* as the English it came from.
 *
 * Words are a translator's business and nothing here has an opinion about them. Format
 * arguments are not: `stringResource(deck_body, left)` passes exactly one value, and a
 * translation that writes `%2$d` — because the sentence reads better with the numbers the
 * other way round, which in German it often does — is a crash at the moment somebody opens
 * that screen, in a language nobody developing the app reads.
 *
 * That is the failure this exists for. It is invisible in review, it cannot happen in the
 * base file, and it gets worse with every language added: twenty files is twenty chances,
 * and the ones most likely to reorder arguments are the ones least likely to be spot-checked.
 *
 * Missing keys are deliberately *not* an error. compose-resources falls back to `values/` per
 * key, so a half-finished translation is a half-translated app rather than a broken one —
 * which is what makes it possible to ship a language before every last string is done.
 */
class TranslationShapeTest {

    @Test
    fun everyTranslationTakesTheSameArgumentsAsTheEnglish() {
        val base = strings(File(resources(), "values/strings.xml"))
        val wrong = mutableListOf<String>()

        translationDirs().forEach { dir ->
            strings(File(dir, "strings.xml")).forEach { (key, text) ->
                val expected = base[key] ?: return@forEach
                if (arguments(text) != arguments(expected)) {
                    wrong += "${dir.name}/$key wants ${arguments(text)}, " +
                        "English passes ${arguments(expected)}"
                }
            }
        }

        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n", prefix = "argument mismatch:\n"))
    }

    /**
     * A key no English string has is a string nothing can ever show.
     *
     * Usually a typo in a key name, and it is silent both ways: the translated line never
     * appears, and the English one is used instead, so the language looks merely incomplete
     * rather than wrong.
     */
    @Test
    fun noTranslationInventsAStringTheGameDoesNotHave() {
        val base = strings(File(resources(), "values/strings.xml")).keys
        val invented = mutableListOf<String>()

        translationDirs().forEach { dir ->
            strings(File(dir, "strings.xml")).keys
                .filterNot { it in base }
                .forEach { invented += "${dir.name}/$it" }
        }

        assertTrue(invented.isEmpty(), "strings nothing will ever read: ${invented.joinToString()}")
    }

    /** `%1$s`, `%2$d` — the positions a line expects, in order. */
    private fun arguments(text: String): List<String> =
        Regex("""%(\d+)\$[a-zA-Z]""").findAll(text).map { it.value }.sorted().toList()

    private fun translationDirs(): List<File> =
        resources().listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }

    private fun resources(): File =
        File("src/commonMain/composeResources").takeIf { it.isDirectory }
            ?: File("composeApp/src/commonMain/composeResources")

    /** The values of a compose-resources strings file, entities resolved. */
    private fun strings(xml: File): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
}
