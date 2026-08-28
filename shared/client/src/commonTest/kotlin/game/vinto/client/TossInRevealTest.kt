package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.hasAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A throw that misses turns the thrown card face up for the table.
 *
 * The rule's price for a wrong toss-in is threefold — the card comes back, a penalty card is
 * drawn, and the player is barred for the round — and the first part happens in the open:
 * the card was thrown face up and everybody watched it checked. The web engine shows the
 * whole attempt to the whole table; this one now announces it the same way a King's wrong
 * name is announced, as a reveal beside the move rather than a fact in the state, because
 * afterwards the card goes back to being one the table is expected to remember.
 *
 * Two tests because there are two paths into a frame: the player's own dispatch, and the
 * bot loop — which used to drop the engine's reveals on the floor, so a bot's misthrow (or
 * wrong King) happened and the one person watching was never shown the card.
 */
class TossInRevealTest {

    @Test
    fun yourOwnMissedThrowIsShownToTheTable() = runTest {
        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.start()

        val rank = session.suppliableRank()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        val hand = session.state.players.first { it.id == me }.cards
        val miss = hand.indexOfFirst { it.rank != rank }
        val thrown = hand[miss]

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        session.dispatch(
            GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(miss))),
        )
        runCurrent()

        val shown = frames.flatMap { it.scenes.flatten() }.filterIsInstance<Beat.Reveal>()
        assertEquals(1, shown.size, "the one card that missed was turned over: $shown")
        assertEquals(Anchor.Seat(me, miss), shown.single().at)
        assertEquals(thrown.rank, shown.single().card.rank, "showing what it really was")
    }

    @Test
    fun aBotsMissedThrowReachesTheHumanAsAReveal() = runTest {
        // The director is the lesson's hook for naming a bot's move; here it names a bad one,
        // because a real bot never throws wrong on purpose and the reveal path through the
        // bot loop deserves a test that does not depend on finding a seed where one blunders.
        var fired = false
        val director = BotDirector { state ->
            val tossIn = state.activeTossIn ?: return@BotDirector null
            if (fired) return@BotDirector null

            val bot = state.players.first { it.isBot }
            val miss = bot.cards.indexOfFirst { it.rank !in tossIn.ranks }
            if (miss < 0) return@BotDirector null

            fired = true
            GameAction.ParticipateInTossIn(ParticipateInTossInPayload(bot.id, listOf(miss)))
        }

        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.EASY, director = director)
        val me = session.playerId
        session.start()

        val rank = session.suppliableRank()
        val bot = session.state.players.first { it.isBot }
        val before = bot.cards.toList()

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        runCurrent()

        val shown = frames.flatMap { it.scenes.flatten() }.filterIsInstance<Beat.Reveal>()
        assertEquals(1, shown.size, "the bot's misthrow was turned over for the table: $shown")
        val reveal = shown.single()
        assertEquals(bot.id, (reveal.at as Anchor.Seat).playerId)
        assertEquals(
            before[(reveal.at as Anchor.Seat).position].rank,
            reveal.card.rank,
            "showing what the bot really threw",
        )

        // And it goes back down: the view goes on hiding it, because remembering it is the
        // table's job — the reveal was the moment, not a new fact about the game.
        val seat = session.view.value.players.first { it.id == bot.id }
        assertTrue(
            seat.cards[(reveal.at as Anchor.Seat).position] !is CardView.Visible,
            "the card is face-down again once the moment has passed",
        )
    }

    private suspend fun LocalGameSession.start() {
        dispatch(GameAction.PeekSetupCard(PositionPayload(playerId, 0)))
        dispatch(GameAction.PeekSetupCard(PositionPayload(playerId, 1)))
        dispatch(GameAction.FinishSetup(PlayerIdPayload(playerId)))
    }

    /** A plain rank the draw pile can still supply, so the staged draw cannot fail. */
    private fun LocalGameSession.suppliableRank(): Rank = Rank.entries.first { rank ->
        !hasAction(rank) && state.drawPile.toList().any { it.rank == rank }
    }
}
