package game.vinto.engine

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.VintoJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A peek is private to whoever peeked, and the engine is what enforces it.
 *
 * A screen declining to draw a card is not privacy — a client we did not write draws whatever
 * it was sent. So this asks from the outside: play the card, then take every seat's view,
 * serialise it, and look for the card's **id**. Ids are `"7_0"`, `"K_2"`, `"Joker1"` — they
 * carry the rank — so an id in a seat's bytes is that seat holding the answer, whatever field
 * it arrived in and whatever a client does with it.
 *
 * Played through the real engine rather than from a hand-built position, because what may be
 * seen turns on what the engine sets when the action starts, and a fixture that sets it itself
 * is a fixture agreeing with itself.
 *
 * Four cards, four rules:
 *
 *  * a **Seven** looks at one of your own — you, and nobody else;
 *  * a **Nine** looks at one of somebody else's — you, and not its owner;
 *  * a **Jack** swaps two with nobody looking, and shows neither to anyone;
 *  * a **King** is aimed before it is declared, so it shows nothing either — the card it
 *    points at is the question, and sending it would be sending the answer.
 */
class PeekPrivacyTest {

    @Test
    fun aSevenShowsYourOwnCardToYouAlone() {
        val game = played(Rank.SEVEN)
        val aimed = unsafeReduce(game.state, target(game.me, game.me, LATER_SLOT))

        onlyOneSeatHolds(aimed, cardAt(aimed, game.me, LATER_SLOT), game.me, "a Seven's peek")
    }

    @Test
    fun aNineShowsAnOpponentsCardToTheLookerAndNotToItsOwner() {
        val game = played(Rank.NINE)
        val aimed = unsafeReduce(game.state, target(game.me, game.opponent, 0))

        onlyOneSeatHolds(aimed, cardAt(aimed, game.opponent, 0), game.me, "a Nine's peek")
    }

    /** The Jack is the control case: it moves two cards without anybody being shown one. */
    @Test
    fun aJackShowsNeitherOfItsCardsToAnybody() {
        val game = played(Rank.JACK)
        var aimed = unsafeReduce(game.state, target(game.me, game.me, LATER_SLOT))
        aimed = unsafeReduce(aimed, target(game.me, game.opponent, 0))

        val mine = cardAt(aimed, game.me, LATER_SLOT)
        val theirs = cardAt(aimed, game.opponent, 0)
        seats(aimed).forEach { seat ->
            assertTrue(mine !in bytes(aimed, seat), "$seat was sent a card a Jack moved blind")
            assertTrue(theirs !in bytes(aimed, seat), "$seat was sent a card a Jack moved blind")
        }
    }

    /**
     * The King points at a card and asks the player to say what it is. Sending the card is
     * sending the answer — and this is what shipped: a King aimed at a card the player had
     * peeked in setup came back face-up, because the rule asked whether the player *knew* it
     * rather than whether the action was showing it.
     */
    @Test
    fun aKingShowsNothingOfTheCardItIsAimedAt() {
        val game = played(Rank.KING)
        val aimed = unsafeReduce(game.state, target(game.me, game.me, PEEKED_SLOT))

        val known = cardAt(aimed, game.me, PEEKED_SLOT)
        seats(aimed).forEach { seat ->
            assertTrue(known !in bytes(aimed, seat), "$seat was handed the King's own question")
        }
    }

    /** And a peek ends: what you saw is yours to remember, not to be sent again. */
    @Test
    fun aPeekedCardIsNotSentAgainOnceTheActionIsOver() {
        val game = played(Rank.NINE)
        val aimed = unsafeReduce(game.state, target(game.me, game.opponent, 0))
        val seen = cardAt(aimed, game.opponent, 0)
        assertTrue(seen in bytes(aimed, game.me), "the looker is shown it while looking")

        val over = unsafeReduce(aimed, confirmPeek(game.me))

        assertTrue(
            over.players.first { it.id == game.me }
                .opponentKnowledge?.get(game.opponent)?.knownCards?.containsKey(0) == true,
            "the engine still knows the looker learned it",
        )
        assertTrue(
            seen !in bytes(over, game.me),
            "but it is no longer sent: remembering it is the player's job",
        )
    }

    // ------------------------------------------------------------------ the measuring

    private fun onlyOneSeatHolds(state: GameState, card: String, seat: String, what: String) {
        assertTrue(card in bytes(state, seat), "$what did not reach $seat, who made it")
        (seats(state) - seat).forEach { other ->
            assertTrue(card !in bytes(state, other), "$what leaked to $other")
        }
    }

    /** One seat's whole view, as the bytes a client would receive. */
    private fun bytes(state: GameState, seat: String): String =
        VintoJson.encodeToString(projectView(state, seat))

    private fun seats(state: GameState) = state.players.map { it.id }

    private fun cardAt(state: GameState, seat: String, position: Int) =
        state.players.first { it.id == seat }.cards[position].id

    private fun target(actor: String, owner: String, position: Int) =
        selectTarget(actor, owner, position)

    /** A dealt game with [rank] played and waiting to be aimed. */
    private fun played(rank: Rank): Game {
        var state = initializeGame(SEED, Difficulty.EASY)
        val me = state.players.first { it.isHuman }.id
        state = unsafeReduce(state, GameAction.PeekSetupCard(PositionPayload(me, PEEKED_SLOT)))
        state = unsafeReduce(state, GameAction.PeekSetupCard(PositionPayload(me, 1)))
        state = unsafeReduce(state, GameAction.FinishSetup(PlayerIdPayload(me)))
        state = unsafeReduce(state, GameAction.SetNextDrawCard(RankPayload(rank)))
        state = unsafeReduce(state, GameAction.DrawCard(PlayerIdPayload(me)))
        state = unsafeReduce(state, GameAction.UseCardAction(PlayerIdPayload(me)))
        return Game(state, me, state.players.first { !it.isHuman }.id)
    }

    private data class Game(val state: GameState, val me: String, val opponent: String)

    private companion object {
        const val SEED = 7L

        /** One the player looked at during setup, and one they did not. */
        const val PEEKED_SLOT = 0
        const val LATER_SLOT = 3
    }
}
