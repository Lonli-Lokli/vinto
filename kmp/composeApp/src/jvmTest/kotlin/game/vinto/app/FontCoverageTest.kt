package game.vinto.app

import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every letter of every language the app ships, checked against the type it ships with.
 *
 * A bundled font is a promise that the app looks the same in every language, and it is a
 * promise that breaks silently: a translator adds a locale, one character is missing from the
 * family, and the platform quietly substitutes its own face for that glyph — so a Romanian
 * "ț" or a Kazakh "ә" arrives in a different typeface, mid-word, and nobody notices until a
 * screenshot. This reads the actual `cmap` out of the actual files and fails the build first.
 *
 * The bundled family is **Fira Sans Condensed**, which carries Latin, its extensions
 * (Vietnamese included) and the whole of Cyrillic — so every language written in either
 * script sets in the same type: Polish, Czech, Romanian, Turkish, Vietnamese, Russian,
 * Ukrainian, Belarusian, Serbian, Kazakh. Scripts it cannot carry — CJK, Arabic, Hebrew,
 * Devanagari, Thai — are a deliberate gap rather than an oversight: no font that covers them
 * fits in a phone game's download, and the platform's own face is the right fallback. This
 * case is what turns adding such a locale into a decision instead of a surprise.
 */
class FontCoverageTest {

    @Test
    fun everyLetterOfEveryTranslationCanBeDrawnInTheBundledFace() {
        val ui = load("fira_medium.ttf")
        val gaps = mutableMapOf<String, MutableSet<Char>>()

        translations().forEach { (locale, text) ->
            text.filter { it.needsAFont() && !ui.canDisplay(it) }
                .forEach { gaps.getOrPut(locale) { mutableSetOf() } += it }
        }

        if (gaps.isNotEmpty()) {
            fail(
                gaps.entries.joinToString(prefix = "the bundled face cannot draw: ") { (locale, missing) ->
                    "$locale needs ${missing.joinToString("")}"
                } + ". Either bundle a face for that script, or record the locale as one that " +
                    "falls back to the platform's own type.",
            )
        }
    }

    /** Every weight has to carry the same alphabet, or a bold word changes typeface. */
    @Test
    fun everyWeightCarriesTheSameAlphabet() {
        val weights = listOf("fira_medium.ttf", "fira_semibold.ttf", "fira_bold.ttf").map(::load)
        val letters = translations().flatMap { (_, text) -> text.toList() }.filter { it.needsAFont() }

        weights.forEach { face ->
            val missing = letters.filterNot(face::canDisplay).toSet()
            assertTrue(missing.isEmpty(), "${face.fontName} cannot draw ${missing.joinToString("")}")
        }
    }

    /**
     * The display face is Latin-only and that is allowed, on one condition: it is used for the
     * name of the game and nothing else, and the name of the game is not translated.
     */
    @Test
    fun theDisplayFaceCoversTheOneStringItIsUsedFor() {
        val wordmark = load("cinzel_bold.ttf")
        val name = strings(File(resources(), "values/strings.xml"))["app_name"]
            ?: fail("there is no app_name to set in it")

        name.filter { it.needsAFont() }.forEach {
            assertTrue(wordmark.canDisplay(it), "the wordmark face cannot draw '$it' of \"$name\"")
        }
    }

    // ------------------------------------------------------------------ the reading

    /**
     * Skips what no text font is expected to carry: spaces, controls, and emoji.
     *
     * Emoji are recognised by being outside the basic plane — 🐞 and 🏆 arrive as surrogate
     * pairs — and not by a codepoint ceiling. The first version of this used a ceiling, which
     * quietly excused every script above it: a whole file of Japanese passed without a word
     * of it being checked, which is precisely the failure this case exists to catch.
     */
    private fun Char.needsAFont(): Boolean =
        !isWhitespace() && !isISOControl() && !isSurrogate() && code !in VARIATION

    private fun translations(): List<Pair<String, String>> =
        resources().listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .flatMap { dir ->
                val xml = File(dir, "strings.xml")
                if (!xml.isFile) emptyList()
                else strings(xml).values.map { dir.name to it }
            }
            .also { assertTrue(it.isNotEmpty(), "no strings found to check") }

    /** The values of a compose-resources strings file, entities resolved. */
    private fun strings(xml: File): Map<String, String> =
        Regex("""<string name="([^"]+)"\s*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml.readText())
            .associate { match ->
                match.groupValues[1] to match.groupValues[2]
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'").replace("\\'", "'")
                    .replace(Regex("""\\n"""), "\n")
                    .replace(Regex("""%\d+\$[sdf]"""), "")
            }

    private fun load(name: String): Font =
        Font.createFont(Font.TRUETYPE_FONT, File(fonts(), name))

    private fun fonts(): File = File(resources(), "font")

    private fun resources(): File {
        // The test's working directory is the module, but that has moved before now.
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
        /** The selectors that ask for an emoji rendering of a character. */
        val VARIATION = 0xFE00..0xFE0F
    }
}
