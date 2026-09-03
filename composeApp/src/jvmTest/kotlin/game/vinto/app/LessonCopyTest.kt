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
 * `TableModelTest` (WORDS.md §6h). After the conversion the script says only *which* beat, so a test in
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

    /**
     * The second beat says what the first assumes: the game is played on what you remember
     * of your own hand, and a high card that bought a look was a fair trade early on. The old
     * opening said every card counts and stopped, which sent newcomers throwing every 9
     * back (product owner).
     */
    @Test
    fun theSecondBeatSaysTheGameIsMemoryAndAHighCardCanBeWorthIt() = runComposeUiTest {
        val words = read { taughtBody(Teaches.Memory) }
        assertTrue(words.contains("remember"), "it does not say the game is memory: $words")
        assertTrue(words.contains("9 or a 10"), "it does not rehabilitate the high lookers: $words")
    }

    /**
     * The plain cards used to be introduced as "what a winning hand is made of", which is
     * untrue: the hand that wins a round is usually at zero or below — Kings and Jokers —
     * and a coalition can nearly always reach that. A player told a hand of 2s and 3s is a
     * winning hand calls Vinto on it and loses.
     */
    @Test
    fun thePlainCardsAreNotCalledAWinningHand() = runComposeUiTest {
        val words = read { taughtBody(Teaches.CardsNumbers) }
        assertTrue(words.contains("zero or less"), "it does not say what a winning hand adds up to: $words")
        assertTrue(!words.contains("winning hand is made of"), "the old claim is still there: $words")
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
     * "Somebody" inside a module with no resources, which is precisely the shape WORDS.md §6h exists
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
     * Somebody else's turn has no heading while they are deciding, and names the card and
     * what it does the moment they play one — the learner's only sight of a 9, a King or an
     * Ace at work is a bot's.
     */
    @Test
    fun aBotPlayingACardIsNamedWithWhatTheCardDoes() = runComposeUiTest {
        val read = mutableStateOf<Pair<String?, String?>?>(null)
        setContent {
            read.value = taughtTitle(Teaches.Watching()) to
                taughtTitle(Teaches.Watching(Speaker.Named("Mikey"), Rank.NINE))
        }
        waitForIdle()

        val (deciding, playing) = read.value!!
        assertNull(deciding, "a bot merely deciding has no heading: $deciding")
        assertTrue(playing!!.contains("Mikey"), "who: $playing")
        assertTrue(playing.contains("Nine"), "which card: $playing")
        assertTrue(playing.contains("Peek at one card of another player"), "and what it does: $playing")
    }

    /** Each of the three cards the coalition plays is explained by what it does. */
    @Test
    fun theCoalitionsCardsAreExplainedByWhatTheyDo() = runComposeUiTest {
        val read = mutableStateOf(emptyList<String>())
        setContent {
            read.value = listOf(
                taughtTitle(Teaches.FinalPlay(Speaker.Named("Raph"), Rank.ACE)) ?: "",
                taughtBody(Teaches.FinalPlay(Speaker.Named("Raph"), Rank.ACE)),
                taughtBody(Teaches.FinalPlay(Speaker.Named("Mikey"), Rank.KING)),
                taughtBody(Teaches.FinalPlay(Speaker.Named("Don"), Rank.NINE)),
                taughtBody(Teaches.CoalitionLeader(Speaker.Named("Raph"))),
            )
        }
        waitForIdle()

        val said = read.value
        assertTrue(said[0].contains("Raph") && said[0].contains("Ace"), "who and what: ${said[0]}")
        assertTrue(said[1].contains("draw a card"), "an Ace makes somebody draw: ${said[1]}")
        assertTrue(said[2].contains("penalty card"), "a wrong name costs a card: ${said[2]}")
        assertTrue(said[3].contains("looks at one card"), "a 9 looks: ${said[3]}")
        assertTrue(said[4].contains("Raph"), "the leader is named: ${said[4]}")
    }

    /** The call the round is built to end on says why the hand cannot be beaten. */
    @Test
    fun theCallNowBeatSaysWhyTheHandIsSafe() = runComposeUiTest {
        val words = read { taughtBody(Teaches.CallNow) }
        assertTrue(words.contains("nothing or less"), "the total: $words")
        assertTrue(words.contains("one more turn"), "and what the call does: $words")
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
