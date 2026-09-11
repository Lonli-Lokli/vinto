package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card on the pile is still a card you may throw in on, even if Vinto has just been called.
 *
 * Vinto is declared at the **end** of a turn, and the turn before it ended with a card landing
 * face up — so the window that card opens and the call arrive together. The confer window was
 * checked above the toss-in, so the moment a bot called, the screen became the coalition's
 * planning band and the throw simply vanished: the window was open at the engine, the cards were
 * still matchable, and there was no way on the screen to use either. Reported from a phone:
 * *"we are missing toss in for the last card, including humans"*.
 *
 * The throw goes first now, and the ordering is not a toss-up. A toss-in is **priced and timed**:
 * it is the one moment that belongs to the whole table at once, a wrong guess costs a card and
 * bars the seat for the rest of the final round, and it closes. The confer window has no clock
 * in a solo game at all — it ends when the person says it does — so it loses nothing by waiting,
 * and the plan it is for is about turns that have not happened yet.
 */
class TheThrowSurvivesTheCallTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val ann = "bot-3"
    private val don = "bot-4"

    @Test
    fun aWindowOpenWhenVintoIsCalledIsStillOfferedToThrowInto() {
        val view = projectView(calledOverAnOpenWindow(), me, conferMsRemaining = 20_000L)

        // The position this is about: both things are true at once.
        assertTrue(view.conferMsRemaining != null, "the fixture has no confer window")
        assertTrue(view.activeTossIn != null, "the fixture has no window to throw into")

        val table = tableFor(view)
        assertTrue(
            table.taps.keys.any { it.playerId == me },
            "the call took the screen and the throw went with it: prompt=${table.prompt}",
        )
        assertTrue(
            table.choices.any { it.move is Move.Send },
            "no way to end the window either, so it cannot be got out of: ${table.choices.map { it.label }}",
        )
    }

    /** A seven lands, and Vinto is called over the top of the window it opened. */
    private fun calledOverAnOpenWindow() = GameState(
        gameId = "throw-survives",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.SEVEN, Rank.TWO)),
            seat(caller, listOf(Rank.THREE)),
            seat(ann, listOf(Rank.FIVE)),
            seat(don, listOf(Rank.SIX)),
        ),
        currentPlayerIndex = 1,
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..5).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.SEVEN, "thrown"))),
        pendingAction = null,
        activeTossIn = ActiveTossIn(
            ranks = listOf(Rank.SEVEN),
            initiatorId = caller,
            originalPlayerIndex = 1,
            participants = emptyList(),
            queuedActions = emptyList(),
            waitingForInput = true,
            playersReadyForNextTurn = emptyList(),
        ),
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = ranks.indices.toList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, ann, don) - id,
        claims = ranks.mapIndexed { index, rank -> Claim(id, listOf(index), listOf(rank)) },
    )
}
