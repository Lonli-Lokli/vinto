package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A planned trade moves the two cards the plan names, and nobody else's.
 *
 * Reported 2026-09-20: *"replay my second planned exchange top king on side 8 was showing like
 * I was exchanging 8 with vinto caller, not with top player"*, and beside it a page that drew
 * the same King on two seats at once.
 *
 * Both come out of one function. A step's [CardAt] carries an **anchor** — the claim that was
 * made about the card — so that a turn can still name a card an earlier turn moved: "the claim
 * travelled with the card, so the claim is where the card is now". `locate` follows it by
 * walking **every seat** and taking the first claim that matches, and a claim is matched on
 * *who said it, what ranks, and covering* — never on whose card it was about. So two claims a
 * seat made about two different hands are indistinguishable, and seat order decides which one
 * the plan means.
 *
 * The Vinto caller is in that walk. A plan may not touch the caller's cards at all, so a step
 * that relocates onto them is not merely the wrong seat — it is a step the round would refuse.
 */
class APlanNamesTheSeatItMeansTest {

    private val me = "human-1"
    private val caller = "bot-1"
    private val nina = "bot-2"

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, claims: List<Claim>) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, nina),
        claims = claims,
    )

    /**
     * The same seat has said "King" about two different hands — the caller's and Nina's — which
     * is an ordinary thing to have happened, and the two claims are identical in everything
     * `sameClaim` compares.
     */
    private fun table(): GameState {
        val said = Claim(by = me, positions = listOf(0), ranks = listOf(Rank.KING))
        return GameState(
            gameId = "anchors",
            roundNumber = 1,
            turnNumber = 12,
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            players = listOf(
                seat(me, listOf(Rank.EIGHT), claims = emptyList()),
                // The caller comes first in the walk, and carries the look-alike claim.
                seat(caller, listOf(Rank.KING), claims = listOf(said)),
                seat(nina, listOf(Rank.KING), claims = listOf(said)),
            ),
            currentPlayerIndex = 0,
            vintoCallerId = caller,
            coalitionLeaderId = null,
            drawPile = Pile((0..4).map { card(Rank.FOUR, "draw-$it") }),
            discardPile = Pile(listOf(card(Rank.THREE, "pile"))),
            pendingAction = null,
            activeTossIn = null,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.MODERATE,
            rngState = 0,
        )
    }

    @Test
    fun aCardAnchoredToATeammatesClaimIsNotFoundOnTheCaller() {
        val view = projectView(table(), me, conferMsRemaining = 20_000L)
        val said = Claim(by = me, positions = listOf(0), ranks = listOf(Rank.KING))

        val found = assertNotNull(
            view.locate(CardAt(nina, 0, anchor = said)),
            "the card the plan named could not be found at all",
        )

        assertEquals(
            nina,
            found.seat,
            "the plan's card was found on ${found.seat}, a seat it was never about",
        )
    }

    @Test
    fun aCardThatHasNotMovedIsFoundWhereItIs() {
        // The anchor is a convenience for a card that has travelled. It must never take one
        // that has not travelled somewhere else.
        val view = projectView(table(), me, conferMsRemaining = 20_000L)
        val said = Claim(by = me, positions = listOf(0), ranks = listOf(Rank.KING))

        assertEquals(
            caller,
            assertNotNull(view.locate(CardAt(caller, 0, anchor = said))).seat,
            "the caller's own card was relocated off it",
        )
    }
}
