package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The UI reads a card's words from `strings.xml`, never from `CardConfig`.
 *
 * The other half of `CardCopyIsDataTest` in `shared/shapes`. That one guards the field that must
 * NOT be translated (`shortDescription`, which becomes `Card.actionText` inside the canonical
 * hash). This one guards the three that must be — and it exists because they silently were not.
 *
 * ## What went wrong, and why nothing caught it
 *
 * WORDS.md §6h moved every sentence the app says into `strings.xml`, slice by slice, and the "?"
 * sheet went with it. What did not go with it were the words those sentences are built FROM:
 * `CardConfig.name`, `longDescription` and `helpText` are Kotlin constants in `shared/shapes`,
 * and they are English.
 *
 * So a Russian player opened the "?" and read a Russian frame with "Queen" in the middle of it
 * and a paragraph of English help underneath. Reported by a player, not by a test — and no test
 * could have caught it, because every string involved *was* a resource lookup and every
 * assertion about the sheet was made in English, where the bug is invisible.
 *
 * A translated template around untranslated nouns is the most convincing way for a screen to
 * look translated and not be, which is why this is worth a test that reads source code.
 *
 * ## Why a source scan rather than a rendering assertion
 *
 * The honest alternative is to render the sheet under a non-English locale and assert nothing
 * English survives — but "nothing English" is not a property that holds: "Vinto" is English-
 * looking on purpose, and so is "J". A test that tried would be a list of exceptions that grew
 * with the copy.
 *
 * Reading the source is exact instead. There is one legitimate way to get a card's words, and
 * it is `CardWords`; every other way is the bug. `StringEscapeTest` beside this one already
 * scans files for the same kind of reason.
 */
class CardCopyIsTranslatedTest {

    @Test
    fun noScreenReadsACardsWordsOutOfTheEngine() {
        // `CardWords.kt` is the one place allowed to know these names — it is the mapping from a
        // rank to its resource, and it names none of the banned fields anyway.
        val offenders = kotlinFiles()
            .filterNot { it.name == "CardWords.kt" }
            .flatMap(::offendingLines)

        if (offenders.isNotEmpty()) {
            fail(
                "A card's name, description and help are COPY: read them through CardWords' " +
                    "cardName/cardLong/cardHelp, never off CardConfig — otherwise English ships " +
                    "inside a translated sentence, which is what the '?' sheet was reported for. " +
                    "See docs/kotlin/WORDS.md §6h.\n\n" +
                    offenders.joinToString(separator = "\n"),
            )
        }
    }

    /** Every banned read in one file, named by line so it can be gone to. */
    private fun offendingLines(file: File): List<String> =
        file.readLines().withIndex().flatMap { (i, line) ->
            val code = line.substringBefore("//")
            BANNED.filter { code.contains(it) && !allowed(it, code) }
                .map { "${file.name}:${i + 1} $it — ${line.trim()}" }
        }

    /**
     * The ONE legitimate read of `shortDescription` in this module.
     *
     * `cardFor` in CardStage builds a `Card` for the King's borrowed rank, and `actionText` is
     * that field — data going into a state object, not copy going onto a screen. Writing anything
     * else there would change what the engine sees.
     */
    private fun allowed(field: String, code: String) =
        field == ".shortDescription" && code.contains("actionText =")

    /**
     * And the accessor that must never exist.
     *
     * `shortDescription` is `Card.actionText`, inside the hash all 50 fixtures pin against
     * TypeScript. There is deliberately no `cardShort()` in `CardWords`: somebody adding one
     * would be building the road to translating it, and this is the sign at the top of it.
     */
    @Test
    fun thereIsNoAccessorForTheFieldThatIsData() {
        // Comments stripped: CardWords' own header explains at length why this field is absent,
        // and a test that read prose would fail on the documentation of the rule it enforces.
        val cardWords = kotlinFiles()
            .single { it.name == "CardWords.kt" }
            .readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lines()
            .joinToString(separator = "\n") { it.substringBefore("//") }
        assertTrue(
            !cardWords.contains("cardShort") && !cardWords.contains("shortDescription"),
            "CardWords has grown a way to read shortDescription. That field is inside the " +
                "canonical hash (CardCopyIsDataTest); translating it diverges all 50 recordings.",
        )
    }

    /** Every Kotlin source of the UI module — the code that draws things a player reads. */
    private fun kotlinFiles(): List<File> =
        File("src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
            .also { assertTrue(it.size > 20, "expected the UI sources, found ${it.size} files") }

    private companion object {
        /**
         * `value` and `action` are deliberately absent: a number and an enum are RULES, not copy,
         * and reading those off `CardConfig` is correct. What is banned is the four fields that
         * are words somebody reads.
         *
         * `config.name` rather than `.name`, because `.name` alone matches every enum in the
         * module. It relies on the convention that the local is called `config` — which every
         * site here follows, and which a reviewer would notice being broken.
         */
        val BANNED = listOf(".longDescription", ".helpText", ".shortDescription", "config.name")
    }
}
