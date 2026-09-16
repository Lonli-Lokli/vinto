package game.vinto.client

import game.vinto.engine.PlayerView
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
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlanEditOutcome
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.TableTalk
import game.vinto.shapes.edited
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The interaction contract: which table the felt is drawing, and what a touch on it may become.
 *
 * The riskiest part of moving the plan onto the table is that a tap there used to be dropped
 * and now means something. Two properties keep that from becoming a move on the real round —
 * the mode is a function of the table alone (design D1), and the composer's own return type
 * excludes the one case that reaches the engine (design D2). Both are held here, at the layer
 * that has no screen to run.
 */
class PlanModeTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val nina = "bot-3"
    private val don = "bot-4"

    // ------------------------------------------------------------------ the mode

    @Test
    fun theModeIsThePlanForTheWholeOfPlanningAndForNothingElse() {
        val standing = CoalitionPlan(lanes = listOf(Lane(nina, swap(me, 0, nina, 0))))

        // Every question about the plan is one mode. The plan itself, and every part of a turn
        // the sentence can open — what becomes of the card, which card goes out, a King's rank,
        // the cards an action names, who an Ace makes draw, who throws in — are all planning,
        // and the felt stays in plan mode through them: a screen that dropped back to the live
        // table to name a rank would put a draw button under a finger in the middle of composing
        // (design D9).
        val planning = listOf(
            Question.ThePlan(),
            Question.Doing(nina, at = 1),
            Question.PuttingDown(nina, at = 1),
            Question.Naming(nina, at = 1, part = Part.Own),
            Question.Aiming(nina, at = 1, part = Part.Own),
            Question.Forcing(nina, at = 1, part = Part.Own),
            Question.Throwing(nina, at = 1, index = 0),
        )
        for (question in planning) {
            assertEquals(
                TableMode.PLAN,
                tableFor(view(), question = question, plan = standing).mode,
                "$question is planning and the felt thought it was drawing the round",
            )
        }

        val elsewhere = listOf(
            Question.None,
            Question.WhichSlot,
            Question.CallRank(0),
            Question.Claiming(about = nina),
        )
        for (question in elsewhere) {
            assertEquals(
                TableMode.LIVE,
                tableFor(view(), question = question, plan = standing).mode,
                "$question put the felt into plan mode",
            )
        }
    }

    @Test
    fun noPhaseOfTheRoundPutsTheFeltIntoPlanMode() {
        // The mode reads the table and nothing else, so nothing about *where the round is* can
        // reach it. Walked over the phases rather than argued, because the guard below is only
        // worth what this is worth.
        val phases = listOf(GamePhase.SETUP, GamePhase.PLAYING, GamePhase.FINAL, GamePhase.SCORING)
        for (phase in phases) {
            for (sub in GameSubPhase.entries) {
                val table = tableFor(view(finalRound().copy(phase = phase, subPhase = sub)))
                assertEquals(TableMode.LIVE, table.mode, "$phase/$sub drew the plan unasked")
            }
        }
    }

    // ------------------------------------------------------------------ the router's guard

    @Test
    fun aPlanEditCanNeverBeAMoveOnTheRound() {
        // Type-level, which is the whole point: the plan composer is *declared* to return a
        // `Move.Quiet`, and `Move.Send` — the one case that reaches the engine — is not one, so
        // a routing bug that dispatched a `GameAction` from a hypothetical table would not
        // compile. The list below is typed as the whole of `Move` and sorted by that
        // membership, which is the hierarchy read back rather than restated.
        val everyKind: List<Move> = listOf(
            Move.Send(GameAction.DrawCard(PlayerIdPayload(me))),
            Move.Ask(Question.ThePlan()),
            Move.Say(TableTalk.WillShed(me, Rank.FIVE)),
            Move.Done,
            Move.Plan(PlanEdit.ClearLane(nina)),
            Move.Agree(true),
        )
        val (routable, dropped) = everyKind.partition { it is Move.Quiet }

        assertTrue(
            dropped.any { it is Move.Send },
            "Move.Send became routable in plan mode: a tap on a ghost would be a move",
        )
        assertTrue(routable.none { it is Move.Send }, "a quiet move is somehow a dispatch")
    }

    @Test
    fun everyMoveTheOpenPlanOffersIsQuiet() {
        // The property the router relies on, read off the real table rather than off a list
        // somebody kept up to date: nothing the open plan puts in front of a player can reach
        // the engine, so routing by mode drops nothing a member wanted.
        val standing = CoalitionPlan(lanes = listOf(Lane(nina, swap(me, 0, nina, 0))))
        val table = tableFor(view(), question = Question.ThePlan(), plan = standing)

        // **Nothing a finger lands on the felt is ever loud**, which is the whole guard: plan
        // mode gives a tap on a card a meaning it never had, and the thing that must not happen
        // is one of those taps turning into a move on the real round (design D2).
        val touches: List<Move> = table.taps.values + table.ranks.map { it.move } + table.seats.map { it.move }
        val loudTouches = touches.filterNot { it is Move.Quiet }
        assertTrue(loudTouches.isEmpty(), "a touch on the plan acts on the round: $loudTouches")

        // The rail is the one place that is allowed something louder, and it is allowed exactly
        // one: the press the coalition's turns are waiting on (`startingTurns`). It is not a
        // `Move.Send` and never becomes one — no `GameAction` leaves this screen — but it does
        // set the round going, so it is named here rather than quietly let through.
        val loudButtons = table.choices.map { it.move }.filterNot { it is Move.Quiet }
        assertTrue(
            loudButtons.all { it is Move.Done },
            "the open plan offers something louder than starting the turns: $loudButtons",
        )
    }

    @Test
    fun aWholeFinalRoundWithThePlanOpenDispatchesNothing() {
        // The invariant, walked rather than argued: every position of the transport, every
        // card of every seat, both paths — the drag's drop map and the felt's own taps — and
        // every button on the rail. If any of it could produce a `Move.Send`, a member reading
        // the plan could move the round by touching a ghost.
        //
        // The rail's own start button is the one exception and is excluded by name below: it is
        // a button, never a card, so no amount of touching a ghost reaches it.
        val standing = CoalitionPlan(
            lanes = listOf(
                Lane(nina, swap(nina, 0, don, 0)),
                Lane(don, null),
                Lane(me, Step.PutDown(CardAt(me, 0, null))),
            ),
        )

        var touched = 0
        for (onPlay in listOf(caller, nina, don, me)) {
            val here = view(finalRound(onPlay = onPlay))
            for (at in 0..standing.lanes.size + 1) {
                for (picked in listOf(null) + everyCard(here)) {
                    val focus = Question.ThePlan(at = at, picked = picked)
                    val table = tableFor(here, question = focus, plan = standing)

                    val offered: List<Move> = table.choices.map { it.move } +
                        table.taps.values +
                        table.ranks.map { it.move } +
                        table.seats.map { it.move } +
                        table.board?.lanes.orEmpty().flatMap { lane ->
                            listOfNotNull(lane.useSuggestion) +
                                lane.composer?.drops.orEmpty().values.flatMap { it.values } +
                                lane.composer?.touches.orEmpty().values
                        } +
                        table.board?.sentence?.clauses.orEmpty().flatMap { clause -> clause.slots.mapNotNull { it.open } } +
                        listOfNotNull(table.board?.sentence?.replay, table.board?.transport?.playAll) +
                        table.board?.transport?.stops.orEmpty().flatMap { listOfNotNull(it.go, it.replay) }

                    touched += offered.size
                    val loud = offered.filterNot { it is Move.Quiet || it is Move.Done }
                    assertTrue(
                        loud.isEmpty(),
                        "with the plan open at $at, picked=$picked, on play $onPlay: $loud",
                    )
                }
            }
        }
        assertTrue(touched > 100, "the walk touched almost nothing ($touched) and proves nothing")
    }

    @Test
    fun readingThePlanMovesNeitherTheDealNorTheTurn() {
        // The other half of the same invariant: the transport is a *reading* of the view, so
        // moving through it must leave the round's own identity where it was. Nothing here
        // reduces, so this is a regression guard on the day somebody makes it.
        val standing = CoalitionPlan(lanes = listOf(Lane(nina, swap(nina, 0, don, 0))))
        val here = view()

        for (at in 0..standing.lanes.size) {
            val positions = rehearsal(here, standing).tables
            for (table in positions) {
                assertEquals(here.gameId, table.gameId, "the plan moved the deal at position $at")
                assertEquals(here.turnNumber, table.turnNumber, "the plan moved the turn at $at")
            }
        }
    }

    /** Every card on the table, as a reference. */
    private fun everyCard(view: PlayerView): List<CardRef> =
        view.players.flatMap { seat -> seat.cards.indices.map { CardRef(seat.id, it) } }

    // ------------------------------------------------------------------ the door, before the offer

    @Test
    fun anEditTheDoorWouldRefuseIsNotOfferedAtAll() {
        // A lane the door refuses must not be a drop target or a selection — offered and then
        // refused reads as a broken control rather than as a rule. Both of the door's refusals
        // are checked against `CoalitionPlan.edited` itself, so the offer cannot drift from it.
        val locked = CoalitionPlan(lanes = listOf(Lane(nina, swap(me, 0, nina, 0), locked = true)))
        val board = assertNotNull(
            tableFor(view(), question = Question.ThePlan(), plan = locked).board,
            "no board to read",
        )

        val lockedLane = assertNotNull(board.lanes.firstOrNull { it.who == speakerFor(view(), nina) })
        assertNull(lockedLane.composer, "a locked lane was offered for editing")

        // And the door agrees: the same edit, put through it, comes back refused. Read against
        // `CoalitionPlan.edited` itself rather than against a copy of its rules, so the offer
        // cannot drift from what would actually be accepted.
        val refused = locked.edited(
            PlanEdit.SetLane(nina, swap(me, 0, don, 0)),
            by = me,
            coalition = listOf(me, nina, don),
            onPlay = caller,
        )
        assertIs<PlanEditOutcome.Refused>(refused, "the door accepted an edit to a locked lane")
    }

    @Test
    fun theSeatOnPlayStaysOpenAndAPlayedTurnDoesNot() {
        // The turn in progress is the one most worth rewriting — its drawn card is the news the
        // plan turns on — so it is offered; the turn before it has been played and is not.
        val onPlay = view(finalRound(onPlay = don))
        val ninas = assertNotNull(tableFor(onPlay, question = Question.ThePlan(at = 1), plan = CoalitionPlan()).board)
        assertNull(
            ninas.lanes.first { it.who == speakerFor(onPlay, nina) }.composer,
            "a played turn was offered for editing",
        )
        val dons = assertNotNull(tableFor(onPlay, question = Question.ThePlan(at = 2), plan = CoalitionPlan()).board)
        assertNotNull(
            dons.lanes.first { it.who == speakerFor(onPlay, don) }.composer,
            "the turn in progress was closed",
        )
    }

    // ------------------------------------------------------------------ fixtures

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, claims: List<Claim>? = null) = PlayerState(
        id = id,
        name = id,
        nickname = id.substringBefore('-').replaceFirstChar { it.uppercase() } + id.last(),
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, nina, don) - id,
        claims = claims,
    )

    private fun finalRound(onPlay: String = caller) = GameState(
        gameId = "plan-mode",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.NINE, Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.NINE)))),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(nina, listOf(Rank.FIVE, Rank.SEVEN), claims = listOf(Claim(nina, listOf(0), listOf(Rank.FIVE)))),
            seat(don, listOf(Rank.SIX), claims = listOf(Claim(don, listOf(0), listOf(Rank.SIX)))),
        ),
        currentPlayerIndex = listOf(me, caller, nina, don).indexOf(onPlay),
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.THREE, "discard-top"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun view(state: GameState = finalRound(), viewer: String = me): PlayerView =
        projectView(state, viewer, conferMsRemaining = 20_000L)

    private fun swap(from: String, fromPos: Int, to: String, toPos: Int) =
        Step.Swap(CardAt(from, fromPos), CardAt(to, toPos))
}
