package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
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
import game.vinto.engine.PublicReveal
import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertNotNull
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
    fun aClaimWearsItsSpeakersFaceAndADisputeWearsBoth() = runComposeUiTest {
        // Without the speaker a badge is anonymous, and an anonymous claim attached to a shared
        // prize is an invitation to claim low cards for yourself so the coalition pushes you.
        val whole = teachingSession().view.value
        val (mate, other) = whole.players.filter { it.id != whole.viewerId }.take(2)
        val caller = whole.players.last { it.id != whole.viewerId && it.id != mate.id && it.id != other.id }
        val disputed = whole.copy(
            phase = GamePhase.FINAL,
            vintoCallerId = caller.id,
            players = whole.players.map { seat ->
                if (seat.id == other.id) {
                    seat.copy(
                        claims = listOf(
                            Claim(mate.id, listOf(0), listOf(Rank.KING)),
                            Claim(whole.viewerId, listOf(0), listOf(Rank.SEVEN)),
                        ),
                    )
                } else {
                    seat
                }
            },
        )

        show(disputed)

        val badge = onAllNodesWithContentDescription("K?7", substring = true).fetchSemanticsNodes()
        assertTrue(badge.isNotEmpty(), "the dispute is not on the card")
        val spoken = badge.first().config.getOrNull(SemanticsProperties.ContentDescription)?.first().orEmpty()
        assertTrue(spoken.contains(mate.nickname), "the badge does not name who said K: $spoken")
        assertTrue(spoken.contains("disputed"), "the badge does not say it is disputed: $spoken")
    }

    @Test
    fun theTwoHalvesOfAPairAreVisiblyLinked() = runComposeUiTest {
        val whole = teachingSession().view.value
        val caller = whole.players.first { it.id != whole.viewerId }
        val paired = whole.copy(
            phase = GamePhase.FINAL,
            vintoCallerId = caller.id,
            players = whole.players.map { seat ->
                if (seat.id == whole.viewerId) {
                    seat.copy(
                        claims = listOf(Claim(seat.id, listOf(0, 2), listOf(Rank.KING, Rank.ACE), covering = true)),
                    )
                } else {
                    seat
                }
            },
        )

        show(paired)

        val linked = onAllNodesWithText("↔", substring = true).fetchSemanticsNodes()
        assertTrue(linked.size == 2, "a pair should link both cards, found ${linked.size}")
    }

    @Test
    fun atScoringEveryClaimIsShownRightOrWrongTheCallersIncluded() = runComposeUiTest {
        // The reveal is the referee (design D12): a tick or a cross on every badge once the
        // hands are face up, the caller's bluff judged on the same terms as anyone's.
        // A real scoring projection, because that is what turns every card face up — a view
        // copied into the scoring phase would still hide them, and a hidden card is never judged.
        val scored = projectView(scoringWithClaims(), "me")

        show(scored)

        assertTrue(
            onAllNodesWithText("✗", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "the caller's wrong claim is not marked wrong",
        )
        assertTrue(
            onAllNodesWithText("✓", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "a true claim is not marked right",
        )
    }

    @Test
    fun aSeatABotIsCoveringIsMarkedOnTheTable() = runComposeUiTest {
        // Without the mark you are negotiating a final round with somebody who has left.
        val whole = teachingSession().view.value
        val away = whole.players.first { it.id != whole.viewerId }.id

        show(whole, away = setOf(away))

        // A mark now rather than the word: colour and text both said too much on this plate, so
        // the durable facts became glyphs — each carrying its own words, which is what this reads.
        val said = onAllNodesWithContentDescription("away", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
        assertTrue(said.isNotEmpty(), "a seat played by a bot is drawn as though its owner were there")
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

        val words = textsOn(view, plan = plan, question = Question.ThePlan())

        // The row names the rank and no more — the sentence it replaced is what a screen reader
        // is given, because a caption that long over a felt is the thing the row was built to
        // remove. Both are asserted, because both are promises.
        assertTrue(
            words.any { it == Rank.KING.serialName },
            "the rank the King names is not on the row: $words",
        )
        val spokenStep = describedOn(view, plan = plan, question = Question.ThePlan())
        assertTrue(
            spokenStep.any { it.contains("declare K", ignoreCase = true) },
            "the step is not said in full for a screen reader: $spokenStep",
        )

        // The nod is on the *seat plate* now (design D7): it is about a member, not about a
        // turn, and putting it among the turns said it was the same kind of thing as a step.
        val spoken = describedOn(view, plan = plan, question = Question.ThePlan())
        assertTrue(
            spoken.any { it.contains("agreed to the plan", ignoreCase = true) },
            "the nod is not worn by the seat that gave it: $spoken",
        )
    }

    @Test
    fun theOpenBoardSaysWhetherThePlanWinsAndABrokenStepIsExplained() = runComposeUiTest {
        // The readout is an outcome, not a number (design D8), and a step whose claim the cards
        // proved wrong is said to be the game working (design D9).
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.TakeTheDiscard)),
            agreed = listOf(mate.id),
            editedBy = mate.id,
        )

        // Where the plan lands is read at the transport's last position (design D10) — the
        // plan has arrived, and the numbers describe the hands on the felt beside them. The
        // last position counts the *coalition's* turns, not the lanes anybody has filled in.
        val turns = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = plan).board).lanes.size
        val landed = Question.ThePlan(at = turns)
        val words = textsOn(view, plan = plan, question = landed)
        assertTrue(
            words.any { it.contains("Our best hand", ignoreCase = true) },
            "the plan does not say where it leaves the round: $words",
        )
        assertTrue(
            words.any { it.contains("believed to hold", ignoreCase = true) },
            "the caller's believed total is missing: $words",
        )
        assertTrue(
            words.any { it.contains("Nobody has spoken about", ignoreCase = true) },
            "a believed total with no count of what is a guess: $words",
        )
        // And no verdict, anywhere (design D12). The comparison is two numbers and both are on
        // the screen; an app that made it would grade every candidate plan and end the argument
        // the round is made of.
        assertTrue(
            words.none { it.contains("wins", ignoreCase = true) || it.contains("falls short", ignoreCase = true) },
            "the plan pronounced a verdict: $words",
        )

        val five = Claim(mate.id, listOf(0), listOf(Rank.FIVE))
        val claimed = view.copy(
            players = view.players.map { seat -> if (seat.id == mate.id) seat.copy(claims = listOf(five)) else seat },
        )
        val broken = CoalitionPlan(
            lanes = listOf(Lane(view.viewerId, Step.Swap(CardAt(view.viewerId, 0), CardAt(mate.id, 0, five)))),
            agreed = listOf(view.viewerId),
            editedBy = view.viewerId,
        )
        val nine = Card("turned", Rank.NINE, 9, played = false, actionText = null)
        // The news rides on the **turn** now (design D13), not on a rail line about the plan as
        // a whole: it is that turn that rests on nothing, and a member reading turn 2 should not
        // have to work out which of the three the warning is about. Read as a screen reader
        // reads it, because a tappable row merges its children's words into its own description.
        // Opened at the turn the broken step is on. The rail draws the turn being composed and
        // no others now — the header's stops are the list — so reading a turn means going to it.
        val revealed = listOf(PublicReveal(mate.id, 0, nine))
        val lanes = assertNotNull(
            tableFor(claimed, question = Question.ThePlan(), plan = broken, reveals = revealed).board,
        ).lanes
        // A stop names the turn it *ends*, so the turn at lane `n` is read at stop `n + 1`.
        val hurt = lanes.indexOfFirst { it.health == game.vinto.client.StepHealth.BROKEN } + 1
        assertTrue(hurt >= 0, "no lane was reported as resting on a disproved claim")

        // Texts as well as descriptions: the news is a line under the row now rather than words
        // merged into a tappable row's own description, because the row is a set of marks and a
        // mark has nowhere to put a sentence.
        val told = describedOn(claimed, plan = broken, question = Question.ThePlan(at = hurt), reveals = revealed) +
            textsOn(claimed, plan = broken, question = Question.ThePlan(at = hurt), reveals = revealed)
        assertTrue(
            told.any { it.contains("proved wrong", ignoreCase = true) },
            "the turn resting on a disproved claim says nothing about it: $told",
        )
    }

    /**
     * The felt says it is a rehearsal by what it *is*, not by a line of text over it.
     *
     * A player who thinks a plan has happened is worse off than one who never planned (design
     * D1), so the mode has to be unmistakable — but it used to be a band reading "REHEARSAL —
     * NOTHING HAS MOVED" that said the same thing on the fortieth second as on the first and
     * could not be pressed. Asked for from a phone: controls, not commentary.
     *
     * Three markers now, all of which change when the mode does and two of which are controls:
     * the switch in the header showing itself on, a stop naming which table is on the felt, and
     * the band ruled in the coalition's colour above it.
     */
    @Test
    fun thePlanSaysItIsOpenWithControlsRatherThanWithASentence() = runComposeUiTest {
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate.id, Step.TakeTheDiscard)),
            agreed = listOf(mate.id),
            editedBy = mate.id,
        )

        show(view, plan = plan, question = Question.ThePlan())

        // One named stop per coalition turn, in the band, and the table as it is among them.
        assertTrue(
            onAllNodesWithContentDescription("Now").fetchSemanticsNodes().isNotEmpty(),
            "the transport does not name the table as it is",
        )
        assertTrue(
            onAllNodesWithContentDescription(mate.nickname).fetchSemanticsNodes().isNotEmpty(),
            "no stop names the turn it ends",
        )

        // And which of them is being read is *said*, not only drawn — the accessibility bar
        // this app ships against, and the whole point of naming the positions at all.
        val lit = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        assertTrue(lit.size == 1, "exactly one stop should be marked as the one being read, found ${lit.size}")

        // The switch that opened it is in the header and shows itself on.
        assertTrue(
            onAllNodesWithContentDescription("Plan", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
            "the plan's switch is not on screen",
        )

        // And none of the sentences it replaced is left behind.
        val words = textsOn(view, plan = plan, question = Question.ThePlan())
        assertTrue(
            words.none { it.contains("Rehearsal", ignoreCase = true) },
            "the rehearsal caption is still on screen: $words",
        )
        assertTrue(
            words.none { it.contains("Carry a card", ignoreCase = true) },
            "help text is still on screen in a game rather than in a lesson: $words",
        )
    }

    @Test
    fun aLaneOwnersAlternativeIsReadOutBesideTheStep() = runComposeUiTest {
        // 3.13: "Nina would rather …" on the open board, as one tappable line.
        val view = conferring()
        val mates = view.players.filter { it.id != view.viewerId && it.id != view.vintoCallerId }
        val set = Step.Swap(CardAt(mates[0].id, 0), CardAt(mates[1].id, 0))
        val rather = Step.Swap(CardAt(mates[0].id, 1), CardAt(mates[1].id, 0))
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mates[0].id, set, suggestion = rather)),
            agreed = listOf(mates[0].id),
            editedBy = view.viewerId,
        )

        // Only on the turn being read (design D8). Parked on that lane it is there; parked past
        // it, three alternative futures on one felt is not a plan anybody can read.
        val lanes = assertNotNull(tableFor(view, question = Question.ThePlan(), plan = plan).board).lanes
        val at = lanes.indexOfFirst { it.step != null } + 1

        show(view, plan = plan, question = Question.ThePlan(at = at))
        assertTrue(
            onAllNodesWithText("would rather", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "the lane owner's alternative was not read out on the turn it belongs to",
        )
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
        show(view, plan = plan, question = Question.ThePlan())
        assertTrue(
            onAllNodesWithText("Agree", ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "a plan stands and there is no way to say yes to it",
        )

        show(view, plan = plan.copy(agreed = listOf(mate.id, view.viewerId)), question = Question.ThePlan())
        assertTrue(
            onAllNodesWithText("Agree", ignoreCase = true).fetchSemanticsNodes().isEmpty(),
            "asked to agree to a plan already agreed to",
        )
    }

    @Test
    fun theWindowOffersTheBoardAndAnEmptyLaneIsTheWayIntoTheComposer() = runComposeUiTest {
        val view = conferring()
        val mate = view.players.first { it.id != view.viewerId && it.id != view.vintoCallerId }

        // A control, not a status line that happens to be tappable. "No plan yet" read as a
        // fact about the game rather than as a door, which is the report this change answers.
        show(view, plan = null)
        assertTrue(
            onAllNodesWithText("Plan", ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            "the window has no way into the plan",
        )

        // An empty turn is drawn as its own empty parts rather than as a gap: the turn exists
        // either way — every turn takes a card from somewhere and does something with it — and
        // it is the empty one a member most needs to fill. Read as a screen reader reads it,
        // because the parts are marks and the marks carry the words.
        val opened = describedOn(view, plan = null, question = Question.ThePlan())
        assertTrue(
            opened.any { it.contains("your call", ignoreCase = true) },
            "an empty turn is not drawn, so nothing invites a plan: $opened",
        )
        assertTrue(
            textsOn(view, plan = null, question = Question.ThePlan()).any {
                it.contains(mate.nickname, ignoreCase = true)
            },
            "the empty turn does not say whose it is",
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

    /**
     * A round scored with two claims standing: the caller bluffed about its King, and the
     * viewer told the truth about their nine.
     */
    private fun scoringWithClaims(): GameState {
        fun card(rank: Rank, id: String) = Card(
            id = id,
            rank = rank,
            value = getCardValue(rank),
            played = false,
            actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
        )
        fun seat(id: String, ranks: List<Rank>, claims: List<Claim>? = null) = PlayerState(
            id = id,
            name = id,
            nickname = id,
            isHuman = id == "me",
            isBot = id != "me",
            cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
            knownCardPositions = ranks.indices.toList(),
            isVintoCaller = id == "caller",
            coalitionWith = if (id == "caller") emptyList() else listOf("me", "nina", "don") - id,
            claims = claims,
        )
        return GameState(
            gameId = "scored",
            roundNumber = 1,
            turnNumber = 20,
            phase = GamePhase.SCORING,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(
                seat("me", listOf(Rank.NINE), claims = listOf(Claim("me", listOf(0), listOf(Rank.NINE)))),
                seat("caller", listOf(Rank.KING), claims = listOf(Claim("caller", listOf(0), listOf(Rank.TWO)))),
                seat("nina", listOf(Rank.FIVE)),
                seat("don", listOf(Rank.SIX)),
            ),
            currentPlayerIndex = 1,
            vintoCallerId = "caller",
            coalitionLeaderId = null,
            drawPile = Pile(emptyList()),
            discardPile = Pile(listOf(card(Rank.THREE, "discard"))),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
    }

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
        reveals: List<PublicReveal> = emptyList(),
    ): List<String> {
        show(view, recent = recent, plan = plan, question = question, reveals = reveals)
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }

    /** Everything the screen says out loud: the marks on the plates are only spoken. */
    private fun ComposeUiTest.describedOn(
        view: PlayerView,
        plan: CoalitionPlan? = null,
        question: Question = Question.None,
        reveals: List<PublicReveal> = emptyList(),
    ): List<String> {
        show(view, plan = plan, question = question, reveals = reveals)
        // Every description of every node, not the first of each: a seat plate is a clickable
        // surface, so it *merges* its children's semantics and reports them as a list. Taking
        // only the first hides every mark after the one the plate happens to draw first.
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
    }

    private fun ComposeUiTest.show(
        view: PlayerView,
        away: Set<String> = emptySet(),
        offered: TableTalk.Proposal? = null,
        recent: List<Say> = emptyList(),
        plan: CoalitionPlan? = null,
        question: Question = Question.None,
        reveals: List<PublicReveal> = emptyList(),
        rehearsing: Boolean = false,
    ) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CompositionLocalProvider(LocalStage provides Stage().apply { this.rehearsing = rehearsing }) {
                        TableScreen(
                            state = TableState(
                                view = view,
                                table = tableFor(
                                    view,
                                    question = question,
                                    away = away,
                                    offered = offered,
                                    plan = plan,
                                    reveals = reveals,
                                ),
                                refusal = null,
                                recent = recent,
                                round = 1,
                            ),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                        )
                    }
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
