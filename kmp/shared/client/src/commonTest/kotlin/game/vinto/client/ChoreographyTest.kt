package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapCardPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What there is to see, and what there deliberately is not.
 *
 * Worth testing rather than watching: a scene is a third of a second long, too short to catch
 * on a screenshot and long enough for a player to notice a card going the wrong way. These are
 * also the cases that will matter most online, where the same function runs against views that
 * arrived over a socket.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `runCurrent`, to drain the scene collector deterministically.
class ChoreographyTest {

    private fun TestScope.scenesOf(session: LocalGameSession): List<List<Scene>> {
        val seen = mutableListOf<List<Scene>>()
        backgroundScope.launch { session.scenes.collect { seen.add(it) } }
        runCurrent()
        return seen
    }

    private suspend fun started(seed: Long = 8L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    private fun List<List<Scene>>.moves(): List<Beat.Move> =
        flatten().flatten().filterIsInstance<Beat.Move>()

    @Test
    fun drawingBringsACardFromTheDeckFaceUp() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val drawn = scenes.last().first().first() as Beat.Move
        assertEquals(Anchor.Deck, drawn.from)
        assertEquals(Anchor.Pending, drawn.to)
        assertEquals(Rank.NINE, drawn.card?.rank, "your own draw flies face-up")
    }

    /**
     * A swap is two cards crossing, and the order is the point: the new one goes into the
     * hand, the old one comes out onto the pile. They are one scene because they happen at
     * once.
     */
    @Test
    fun aSwapIsOneSceneOfTwoCardsCrossing() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(GameAction.SwapCard(SwapCardPayload(session.playerId, 2)))
        runCurrent()

        val scene = scenes.last().first()
        assertEquals(2, scene.size, "one scene, two beats")

        val seat = Anchor.Seat(session.playerId, 2)
        val (into, out) = scene.map { it as Beat.Move }
        assertEquals(Anchor.Pending to seat, into.from to into.to)
        assertEquals(Rank.FIVE, into.card?.rank)
        assertEquals(seat to Anchor.Discard, out.from to out.to)
    }

    /** A card somebody else drew is not yours to see, in the air any more than on the table. */
    @Test
    fun aCardDrawnByABotFliesFaceDown() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val draws = scenes.moves().filter { it.from == Anchor.Deck && it.to == Anchor.Pending }
        assertTrue(draws.size > 1, "the bots drew too: ${draws.size}")
        assertNull(draws.last().card, "and what they drew is not shown")
    }

    /** A peek turns a card over where it lies. Nothing travels. */
    @Test
    fun aPeekTurnsACardOverRatherThanMovingIt() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        val target = session.table().taps.keys.first()
        session.dispatch((session.table().taps.getValue(target) as Move.Send).action)
        runCurrent()

        val turns = scenes.flatten().flatten().filterIsInstance<Beat.Turn>()
        assertEquals(1, turns.size, "one card turned over")
        assertEquals(Anchor.Seat(target.playerId, target.position), turns.single().at)
        assertTrue(scenes.moves().isEmpty(), "and nothing moved")
    }

    /**
     * A penalty is a card arriving in a hand, and it is derived from the hand growing rather
     * than from the action that caused it — several actions can, and one added later animates
     * without anybody remembering to teach the choreography about it.
     */
    @Test
    fun aWrongCallIsSeenAsACardArrivingAndAHandFlinching() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        // Position 4 was never peeked, so calling a rank for it is a guess — and a wrong one
        // unless the deal is very kind.
        session.dispatch(GameAction.SwapCard(SwapCardPayload(session.playerId, 4, Rank.KING)))
        runCurrent()

        val beats = scenes.flatten().flatten()
        val flinched = beats.filterIsInstance<Beat.Flinch>()
        if (flinched.isEmpty()) return@runTest // the call happened to be right; nothing owed

        assertTrue(
            beats.any { it is Beat.Move && it.from == Anchor.Deck && it.to == flinched.first().at },
            "the penalty card came off the deck to where the hand flinched",
        )
        assertTrue(
            beats.any { it is Beat.Say && it.playerId == session.playerId },
            "and the seat said something about it",
        )
    }

    @Test
    fun callingVintoIsAnnouncedRatherThanMoved() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(session.table().send("Call Vinto"))
        runCurrent()

        val beats = scenes.flatten().flatten()
        assertTrue(beats.any { it is Beat.Say && it.line == "Vinto!" }, "somebody said it")
        assertTrue(
            beats.any { it is Beat.Attend && it.kind == Attention.VINTO },
            "and the table was pointed at them",
        )
    }

    private fun Table.send(startsWith: String): GameAction {
        val choice = choices.first { it.label.startsWith(startsWith) }
        return (choice.move as Move.Send).action
    }

    private fun LocalGameSession.table() = tableFor(view.value)
}
