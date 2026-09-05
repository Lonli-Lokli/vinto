package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Label
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.Say
import game.vinto.client.spoken
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the coalition's own screens actually draw.
 *
 * The model beneath these is covered next door in `shared/client`, and that is exactly why
 * this file exists: a table model with no screen reading it is a library rather than a
 * feature, and every one of these behaviours reached a player only after something in
 * `composeApp` was wired to it. A test at this layer is what would notice if that wiring came
 * out again.
 */
@OptIn(ExperimentalTestApi::class)
class CoalitionScreenTest {

    @Test
    fun theConferWindowSaysWhatItIsForAndHowToLeaveIt() = runComposeUiTest {
        // Online this was a twenty-second stall with no button and nothing saying why.
        val view = conferring()

        val words = textsOn(view)

        assertTrue(
            words.any { it.contains("what you know", ignoreCase = true) },
            "the window does not say what it is for: $words",
        )
        assertTrue(
            words.any { it.contains("Done talking", ignoreCase = true) },
            "a window with no way out: $words",
        )
    }

    @Test
    fun everyCardIsTouchableWhileTheCoalitionConfers() {
        // The window is for pooling what the table knows, so every card has to be claimable —
        // a teammate's and the caller's, not only your own.
        val table = tableFor(conferring())

        val seats = table.taps.keys.map { it.playerId }.toSet()
        assertTrue(seats.size > 1, "only one hand could be spoken about: $seats")
        assertTrue(table.choices.any { it.move is Move.Done }, "no way to finish talking")
    }

    @Test
    fun aClaimIsWornOnTheCardForTheWholeTableToRead() = runComposeUiTest {
        val whole = teachingSession().view.value
        val mate = whole.players.first { it.id != whole.viewerId }
        val told = whole.copy(
            phase = GamePhase.FINAL,
            vintoCallerId = whole.players.last { it.id != whole.viewerId && it.id != mate.id }.id,
            players = whole.players.map { seat ->
                if (seat.id == mate.id) {
                    seat.copy(claims = listOf(Claim(whole.viewerId, listOf(0), listOf(Rank.QUEEN))))
                } else {
                    seat
                }
            },
        )

        show(told)

        assertTrue(
            onAllNodesWithText("Q", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "a standing claim is not drawn on the card it is about",
        )
    }

    @Test
    fun aSeatABotIsCoveringIsMarkedOnTheTable() = runComposeUiTest {
        // Without the mark you are negotiating a final round with somebody who has left.
        val whole = teachingSession().view.value
        val away = whole.players.first { it.id != whole.viewerId }.id

        show(whole, away = setOf(away))

        val words = onAllNodesWithText("away", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
        assertTrue(words.isNotEmpty(), "a seat played by a bot is drawn as though its owner were there")
    }

    @Test
    fun aSuggestionAddressedToYouArrivesAsTwoButtons() = runComposeUiTest {
        // Accepting sends the *viewer's own* action, so the screen has to offer it as a move
        // rather than as a notification — and declining has to be there beside it, because a
        // suggestion that could only be ignored leaves the proposer watching nothing happen.
        val view = suggestedTo()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val offered = TableTalk.Proposal(
            by = mate.id,
            to = view.viewerId,
            move = GameAction.DrawCard(PlayerIdPayload(view.viewerId)),
        )

        show(view, offered = offered)

        assertTrue(
            onAllNodesWithText("Do that", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "a suggestion arrived with no way to take it",
        )
        assertTrue(
            onAllNodesWithText("No, thanks", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "a suggestion arrived with no way to refuse it out loud",
        )
    }

    @Test
    fun aSuggestionMeantForSomebodyElseIsNotOfferedToYou() {
        // The addressee check is the seat boundary wearing a different hat: a screen that drew
        // somebody else's suggestion as a button would be a screen offering to act for them.
        val view = suggestedTo()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val elsewhere = TableTalk.Proposal(
            by = view.viewerId,
            to = mate.id,
            move = GameAction.DrawCard(PlayerIdPayload(mate.id)),
        )

        val table = tableFor(view, offered = elsewhere)

        assertTrue(
            table.choices.none { it.label == Label.DoAsSuggested },
            "a move for another seat was offered to this one",
        )
    }

    /**
     * Every sentence the phrasebook can produce reaches the log strip as words.
     *
     * The `when` in `Said.kt` is exhaustive, so a *missing* branch is a compile error — what
     * this catches is the other half, a branch wired to a string that is blank or absent, which
     * compiles perfectly and draws a coalition talking in silence. It walks the whole sealed
     * hierarchy rather than a sample, so a ninth sentence added later cannot ship undrawn.
     */
    @Test
    fun everySentenceTheCoalitionCanSpeakIsDrawnInTheLog() = runComposeUiTest {
        val view = suggestedTo()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val nicknames = view.players.associate { it.id to it.nickname }

        // Against the *silent* table, because a nickname is drawn on the felt whether anybody
        // has spoken or not — comparing to it is what makes this a test of the strip.
        val silent = textsOn(view, recent = emptyList())

        for (talk in wholePhrasebook(mate.id, view.viewerId)) {
            val line = spoken(talk, view.viewerId, nicknames)

            val added = textsOn(view, recent = listOf(line)) - silent.toSet()

            assertTrue(
                added.any { it.isNotBlank() },
                "${talk::class.simpleName} draws nothing the silent table did not: $added",
            )
            assertTrue(
                added.any { it.contains(mate.nickname) },
                "${talk::class.simpleName} reaches the strip without naming who said it: $added",
            )
        }
    }

    @Test
    fun theBoardIsDrawnInWordsWithWhoHasAgreed() = runComposeUiTest {
        // A plan a screen does not draw is a library, not a feature — the very defect the
        // reviews kept finding one layer down. The lane's step, the nods and the last editor
        // all have to be on the rail in the reader's own language.
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.Declare(Rank.KING))),
            agreed = listOf(mate.id),
            editedBy = mate.id,
        )

        val words = textsOn(view, plan = plan, question = Question.ThePlan)

        assertTrue(words.any { it.contains("declare K", ignoreCase = true) }, "the step is not in words: $words")
        assertTrue(words.any { it.contains(mate.nickname) && it.contains("✓") }, "the nod is not drawn: $words")
        assertTrue(words.any { it.contains("last changed by", ignoreCase = true) }, "no editor named: $words")
    }

    @Test
    fun agreeingIsOneTapUntilYouHave() = runComposeUiTest {
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.TakeTheDiscard)),
            agreed = listOf(mate.id),
            editedBy = mate.id,
        )

        // The whole word, not a substring: "3 of 3 agreed" and "Nina agreed" are on the board
        // too, and neither is a button.
        show(view, plan = plan, question = Question.ThePlan)
        assertTrue(
            onAllNodesWithText("Agree", ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "a plan stands and there is no way to say yes to it",
        )

        show(view, plan = plan.copy(agreed = listOf(mate.id, view.viewerId)), question = Question.ThePlan)
        assertTrue(
            onAllNodesWithText("Agree", ignoreCase = true).fetchSemanticsNodes().isEmpty(),
            "asked to agree to a plan already agreed to",
        )
    }

    @Test
    fun theWindowOffersTheBoardAndAnEmptyLaneIsTheWayIntoTheComposer() = runComposeUiTest {
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }

        // The line is one tappable row, so its words are merged into one node: matched by text
        // rather than read off the list of texts.
        show(view, plan = null)
        assertTrue(
            onAllNodesWithText("No plan yet", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "the window has no way into the board",
        )

        val opened = textsOn(view, plan = null, question = Question.ThePlan)
        assertTrue(
            opened.any { it.contains(mate.nickname, ignoreCase = true) && it.contains("your call", ignoreCase = true) },
            "an empty lane is not drawn, so nothing invites a plan: $opened",
        )
    }

    @Test
    fun yourOwnLaneIsUnderThePromptOnYourTurn() = runComposeUiTest {
        val view = suggestedTo()
        val plan = CoalitionPlan(
            lanes = listOf(Lane(view.viewerId, Step.TakeTheDiscard)),
            agreed = listOf(view.viewerId),
            editedBy = view.viewerId,
        )

        val words = textsOn(view, plan = plan)

        assertTrue(
            words.any {
                it.contains(
                    "The plan:",
                    ignoreCase = true,
                ) && it.contains("take the discard", ignoreCase = true)
            },
            "the viewer's lane is not written on their turn: $words",
        )
    }

    /** One of each, spoken by [by] — the compiler is what keeps this list complete. */
    private fun wholePhrasebook(by: String, to: String): List<TableTalk> = listOf(
        TableTalk.Proposal(by, to, GameAction.DrawCard(PlayerIdPayload(to))),
        TableTalk.GiveMe(by, to, position = 0),
        TableTalk.TakeThis(by, position = 1),
        TableTalk.IWill(by, GameAction.DrawCard(PlayerIdPayload(by))),
        TableTalk.WillShed(by, Rank.QUEEN),
        TableTalk.Standing(by, TableTalk.Standing.Where.LOW),
        TableTalk.PlayFor(by, to),
        TableTalk.Answer(by, to, TableTalk.Answer.Says.YES),
    )

    /** A final round somebody else called, with this seat's confer window open. */
    private fun conferring(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(
            phase = GamePhase.FINAL,
            vintoCallerId = caller.id,
            conferMsRemaining = 20_000L,
        )
    }

    /** This seat, on turn, in a final round somebody else called. */
    private fun suggestedTo(): PlayerView {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        return whole.copy(phase = GamePhase.FINAL, vintoCallerId = caller.id)
    }

    private fun ComposeUiTest.textsOn(
        view: PlayerView,
        recent: List<Say> = emptyList(),
        plan: CoalitionPlan? = null,
        question: Question = Question.None,
    ): List<String> {
        show(view, recent = recent, plan = plan, question = question)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }

    private fun ComposeUiTest.show(
        view: PlayerView,
        away: Set<String> = emptySet(),
        offered: TableTalk.Proposal? = null,
        recent: List<Say> = emptyList(),
        plan: CoalitionPlan? = null,
        question: Question = Question.None,
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(
                            view = view,
                            table = tableFor(view, question = question, away = away, offered = offered, plan = plan),
                            refusal = null,
                            recent = recent,
                            round = 1,
                        ),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
