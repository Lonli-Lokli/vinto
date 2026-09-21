package game.vinto.client

import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.engine.projectView
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A button the player can reach is a move the engine will take.
 *
 * `CountRefusals` says it in as many words: *"Every refusal is a defect by construction: the
 * controls are drawn from the same `Table` the validator judges, so a button the player could
 * reach is a move the engine agreed to before it was offered."* It is not decoration — the
 * player's half of a refusal is a tap that does nothing but print a line of small text, and the
 * only reason anybody hears about it is that the app reports itself to Sentry when it happens.
 *
 * Which is how this arrived: `MOVE_REFUSED in SOLO`, from a phone, at 15:37 on 2026-09-21.
 *
 * The engine had learnt a refusal the table never learnt to match. Vinto is declared at the end
 * of a turn, and a card you have thrown into your own window is owed its action, so the turn is
 * not over until it has played (`TheCallWaitsForTheCallersOwnThrowTest`, and four corpus tails
 * regenerated for it). `ActionValidator` refuses the call; `tossInTable` went on offering it.
 * Throw a card in on your own turn and the **Call Vinto** button was still there, and still did
 * nothing.
 *
 * So the assertion is the sentence above rather than the one case: every choice on offer is run
 * through the validator that will judge it.
 */
class NothingOnOfferIsRefusedTest {

    private val me = "human-1"
    private val ann = "bot-2"
    private val ben = "bot-3"
    private val cal = "bot-4"

    @Test
    fun aTurnWithAThrowOwedOffersNothingTheEngineWillRefuse() {
        val state = myOwnWindowWithMyThrowInIt()
        val table = tableFor(projectView(state, me))

        val refused = table.choices
            .mapNotNull { choice -> (choice.move as? Move.Send)?.let { choice.label to it.action } }
            .mapNotNull { (label, action) ->
                (ActionValidator.validate(state, action) as? Validation.Invalid)
                    ?.let { "$label → ${it.reason}" }
            }

        assertEquals(
            emptyList(),
            refused,
            "the table offered a move the engine refuses, which is the tap that does nothing",
        )
    }

    /**
     * My turn, my window, and a card of mine already in it.
     *
     * The shape the report came from: a seven lands on my own turn, I throw my other seven in
     * by touching it, and the window is still open with my throw queued behind it.
     */
    private fun myOwnWindowWithMyThrowInIt() = GameState(
        gameId = "nothing-refused",
        roundNumber = 1,
        turnNumber = 9,
        phase = GamePhase.PLAYING,
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        finalTurnTriggered = false,
        players = listOf(
            seat(me, listOf(Rank.SEVEN, Rank.TWO)),
            seat(ann, listOf(Rank.THREE)),
            seat(ben, listOf(Rank.FIVE)),
            seat(cal, listOf(Rank.SIX)),
        ),
        currentPlayerIndex = 0,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = Pile((0..5).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.SEVEN, "played"))),
        pendingAction = null,
        activeTossIn = ActiveTossIn(
            ranks = listOf(Rank.SEVEN),
            initiatorId = me,
            originalPlayerIndex = 0,
            participants = listOf(me),
            // The throw that is owed its action, and the whole of the report.
            queuedActions = listOf(TossInAction(playerId = me, rank = Rank.SEVEN, position = 0)),
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
        isVintoCaller = false,
        coalitionWith = emptyList(),
    )
}
