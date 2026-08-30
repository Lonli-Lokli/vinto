package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.client.Gloss
import game.vinto.client.Speaker
import game.vinto.client.Teaches
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the lesson actually says, now that it says it from resources.
 *
 * The other half of `TeachScriptTest`, split the same way `CardHelpTest` was split off
 * `TableModelTest` (§6h). After the conversion the script says only *which* beat, so a test in
 * `shared/client` asserting "the body contains +3" has nothing to assert on — and the easy,
 * wrong move at that point is to let the claim go and call the suite green.
 *
 * These are the claims that were about to be lost. Every one of them is a rule the lesson
 * teaches, and one of them is here because the copy got a rule **wrong** once: a caller who
 * finishes lower takes +3 *and the others each lose one*, where nothing is what a tie costs
 * them. A tutorial that teaches a scoring rule incorrectly is worse than one that skips it,
 * because the player believes it.
 */
@OptIn(ExperimentalTestApi::class)
class LessonCopyTest {

    /**
     * Renders one composable string and hands it back.
     *
     * These renderers are `@Composable` because `stringResource` is, so reading one means
     * composing it — the same shape `CardHelpTest` uses.
     */
    private fun ComposeUiTest.read(
        of: @Composable () -> String,
    ): String {
        val slot = mutableStateOf("")
        setContent { slot.value = of() }
        waitForIdle()
        val words by slot
        return words
    }

    @Test
    fun theOpeningSaysWhatTheGameIsFor() = runComposeUiTest {
        val words = read { taughtBody(Teaches.Welcome) }
        assertTrue(
            words.contains("lowest hand wins", ignoreCase = true),
            "the first thing said is not the object of the game: $words",
        )
    }

    @Test
    fun theCallSaysWhatCallingDoes() = runComposeUiTest {
        val words = read { taughtBody(Teaches.VintoCalled(Speaker.Named("Raph"))) }
        assertTrue(words.contains("one more turn"), "it does not say what a call does: $words")
    }

    @Test
    fun theCallerIsNamedInTheHeading() = runComposeUiTest {
        val read = mutableStateOf<String?>("")
        setContent { read.value = taughtTitle(Teaches.VintoCalled(Speaker.Named("Raph"))) }
        waitForIdle()

        val heading by read
        assertTrue(heading!!.contains("Raph"), "the caller is not named: $heading")
    }

    /**
     * A caller the view could not name. This used to interpolate the literal English
     * "Somebody" inside a module with no resources, which is precisely the shape §6h exists
     * to remove.
     */
    @Test
    fun aCallerWithNoNameStillGetsAHeading() = runComposeUiTest {
        val read = mutableStateOf<String?>("")
        setContent { read.value = taughtTitle(Teaches.VintoCalled(Speaker.Nobody)) }
        waitForIdle()

        val heading by read
        assertTrue(heading!!.isNotBlank(), "an unnamed caller left the heading empty")
        assertTrue(heading!!.contains("Vinto"), heading!!)
    }

    @Test
    fun theCoalitionRuleIsGivenInBothHalves() = runComposeUiTest {
        val words = read { taughtBody(Teaches.Coalition) }
        assertTrue(words.contains("single best hand"), "only the best hand counts: $words")
        assertTrue(words.contains("caller's cards"), "and the caller is untouchable: $words")
    }

    /** All three outcomes of a round, with the right numbers. See the class comment. */
    @Test
    fun theScoringLessonGivesAllThreeOutcomes() = runComposeUiTest {
        val words = read { taughtBody(Teaches.Scoring) }

        assertTrue(words.contains("+3"), "the winning number: $words")
        assertTrue(words.contains("loses 1"), "and the losing one: $words")
        assertTrue(
            words.contains("everybody else loses 1"),
            "a caller who finishes lower costs the others a point each: $words",
        )
        assertTrue(
            words.contains("Level"),
            "and only a tie leaves them on nothing: $words",
        )
    }

    @Test
    fun thePlayerIsToldWhenTheyMayCall() = runComposeUiTest {
        val words = read { taughtBody(Teaches.YourTurnToCall) }
        assertTrue(
            words.contains("end of any turn of yours"),
            "it does not say when the button may be pressed: $words",
        )
    }

    /**
     * The toss-in beat is the one with a computed prefix, and the case that matters is the
     * empty one: a window nobody else has thrown into has to read as an ordinary sentence
     * rather than as a sentence with a hole at the front.
     */
    @Test
    fun aTossInWindowNamesWhoeverHasAlreadyThrown() = runComposeUiTest {
        val alone = read { taughtBody(Teaches.TossIn(emptyList())) }
        assertTrue(alone.startsWith("The moment"), "an empty prefix left something behind: $alone")

        val watched = read { taughtBody(Teaches.TossIn(listOf("Raph", "Mikey"))) }
        assertTrue(watched.startsWith("Raph and Mikey"), watched)
        assertTrue(watched.contains("The moment"), "the rest of the beat went missing: $watched")
    }

    /** Two beats have no heading, and the renderer is what says so. */
    @Test
    fun theTwoHeadlessBeatsHaveNoHeading() = runComposeUiTest {
        val read = mutableStateOf<String?>("unset")
        setContent { read.value = taughtTitle(Teaches.PeeksEnd) }
        waitForIdle()
        assertNull(read.value, "a beat that should have no heading has one: ${read.value}")
    }

    /**
     * A card met for the first time, in the game's own words — `CARD_CONFIGS`, the same copy
     * the help sheet shows, so the lesson cannot teach a rule the rest of the game does not
     * have. What moved to resources is the frame around them.
     */
    @Test
    fun aCardMetForTheFirstTimeIsNamedPricedAndExplained() = runComposeUiTest {
        val read = mutableStateOf("")
        setContent { read.value = noteOn(Rank.SEVEN) }
        waitForIdle()

        val note by read
        assertTrue(note.startsWith("Seven"), "the card is not named first: $note")
        assertTrue(note.contains("7"), "it does not say what holding one costs: $note")
        assertTrue(
            note.contains("Peek at one of your own cards"),
            "the note is not the card's own copy: $note",
        )
    }

    @Test
    fun aPlainCardSaysItDoesNothing() = runComposeUiTest {
        val read = mutableStateOf("")
        setContent { read.value = noteOn(Rank.FOUR) }
        waitForIdle()

        val note by read
        assertTrue(note.startsWith("Four"), note)
        assertTrue(note.contains("nothing at all"), "a 4 should say it cannot do anything: $note")
    }

    /** Every gloss says something, and no two of them say the same thing. */
    @Test
    fun everyGlossIsItsOwnLine() = runComposeUiTest {
        val read = mutableStateOf(emptyList<String>())
        setContent { read.value = Gloss.entries.map { glossed(it) } }
        waitForIdle()

        val lines by read
        assertTrue(lines.none { it.isBlank() }, "a gloss came back empty: $lines")
        assertEquals(Gloss.entries.size, lines.distinct().size, "two glosses say the same thing")
    }
}
