package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.projectView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A seat with no cards, drawing.
 *
 * Reported from a phone: *"if player has no cards he can draw and just put this card in his
 * hand"*. The engine half is `AnEmptyHandStillTakesItsTurnTest`; this is the table above it.
 *
 * "Swap Cards" opened a question — which of your cards does it replace? — whose answer was an
 * empty list of taps, so the turn dead-ended on a screen with nothing on it. With no cards
 * there is no slot to choose and no card going out to guess at, so the whole question is
 * skipped and the one button says what actually happens: keep it.
 */
class AnEmptyHandKeepsWhatItDrawsTest {

    private val me = "human-1"

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
        nickname = id.substringBefore('-').replaceFirstChar { it.uppercase() } + id.last(),
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.map { card(it, "$id-${it.serialName}") },
        knownCardPositions = emptyList(),
        isVintoCaller = false,
        coalitionWith = emptyList(),
    )

    private fun drawing(mine: List<Rank>): PlayerView {
        val state = GameState(
            gameId = "empty",
            roundNumber = 1,
            turnNumber = 4,
            phase = GamePhase.PLAYING,
            subPhase = GameSubPhase.CHOOSING,
            finalTurnTriggered = false,
            players = listOf(
                seat(me, mine),
                seat("bot-2", listOf(Rank.FIVE)),
                seat("bot-3", listOf(Rank.SIX)),
                seat("bot-4", listOf(Rank.SEVEN)),
            ),
            currentPlayerIndex = 0,
            vintoCallerId = null,
            coalitionLeaderId = null,
            drawPile = Pile((0..4).map { card(Rank.FOUR, "draw-$it") }),
            discardPile = Pile(listOf(card(Rank.THREE, "discard-top"))),
            pendingAction = PendingAction(
                card = card(Rank.JOKER, "drawn"),
                playerId = me,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.DRAWING,
                targets = emptyList(),
            ),
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
        return projectView(state, me)
    }

    @Test
    fun withNoCardsTheKeepIsOneTouchAndNamesNothing() {
        val table = tableFor(drawing(emptyList()))
        val keep = table.choices.first { it.label == Label.KeepIt }

        val kept = (keep.move as Move.Send).action as GameAction.SwapCard
        assertEquals(0, kept.payload.position, "the empty place is position 0")
        assertNull(kept.payload.declaredRank, "nothing goes out, so there is nothing to name")

        // The slot question is not offered at all: it would open on an empty list of taps.
        assertTrue(table.choices.none { it.label == Label.SwapCards }, "${table.choices.map { it.label }}")
        assertTrue(tableFor(drawing(emptyList()), Question.WhichSlot).taps.isEmpty())
    }

    @Test
    fun aHandWithCardsStillAsksWhichOne() {
        val table = tableFor(drawing(listOf(Rank.TWO, Rank.KING)))
        assertEquals(Move.Ask(Question.WhichSlot), table.choices.first { it.label == Label.SwapCards }.move)
        assertTrue(table.choices.none { it.label == Label.KeepIt }, "${table.choices.map { it.label }}")
        assertEquals(2, tableFor(drawing(listOf(Rank.TWO, Rank.KING)), Question.WhichSlot).taps.size)
    }
}
