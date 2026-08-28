package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.cardInPlay
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Nothing on this table teleports.
 *
 * The rule stated plainly: if a card is in one place and then in another, the player watched
 * it go. Individual cases cover individual cards — the draw, the swap, the played card — and
 * this covers the ones nobody thought to write a case for, by playing a game out and checking
 * every frame of it against the position it left behind.
 *
 * A move is any change to where the cards are: a hand growing or shrinking, the pile growing,
 * the deck shrinking, a card arriving in front of somebody or leaving. There is exactly one
 * carve-out, and it is not a loophole: a card whose action is being played is already lying on
 * the discard — that is what makes the toss-in window legitimate — so the engine finally
 * recording the discard moves nothing that was not already there.
 */
class EveryMoveIsSeenTest {

    @Test
    fun everythingThatMovesIsAnimated() = runTest {
        val session = LocalGameSession(seed = 12L, difficulty = Difficulty.EASY)
        val me = session.playerId

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        runCurrent()

        var before = session.view.value
        var checked = 0

        repeat(TURNS) {
            if (session.isOver) return@repeat
            frames.clear()

            // A bot may call Vinto mid-game; the bots then hold for this seat's leader
            // choice, and the one turn left is still an ordinary draw-and-discard. The
            // script answers the prompt and plays on — the frames are what is under test,
            // not the game's shape.
            val view = session.view.value
            if (view.vintoCallerId != null && view.coalitionLeaderId == null &&
                view.vintoCallerId != me
            ) {
                val leader = view.players.first { it.id != view.vintoCallerId }
                session.dispatch(
                    GameAction.SetCoalitionLeader(game.vinto.shapes.LeaderIdPayload(leader.id)),
                )
            }

            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
            runCurrent()

            frames.forEach { frame ->
                val moved = whatMoved(before, frame.view)
                if (moved != null) {
                    checked++
                    assertTrue(
                        frame.scenes.flatten().any { it is Beat.Move || it is Beat.Reshuffle },
                        "${frame.action::class.simpleName} moved a card and showed nothing: $moved",
                    )
                }
                before = frame.view
            }
        }

        assertTrue(checked > MANY, "a game's worth of movement to check: $checked")
    }

    /** What is in a different place than it was, or null if nothing is. */
    private fun whatMoved(before: PlayerView, after: PlayerView): String? {
        // A card in play is lying on the pile already — that is what makes its toss-in window
        // legitimate — so the engine finally writing it into the pile moves nothing. It may
        // land *beneath* the top, where an unplayed action is still sitting, which is why this
        // is counted rather than matched by id.
        val settled = before.cardInPlay != null && after.discardCount == before.discardCount + 1

        // The mirror of it: a card taken off the pile to be played is still lying on the pile,
        // because that is where a card whose action is running is drawn. Option B moves
        // nothing at all.
        val taken = before.discardTop != null && before.discardTop?.id == after.cardInPlay?.id

        // And a pending action appearing or vanishing is a movement only when the card was
        // not already lying on the pile at either end of it. A card in play is on the pile for
        // the whole of its action, so it neither arrives nor leaves when the action does: the
        // journey that put it there was animated when it was played, thrown, or taken.
        val onThePile = before.cardInPlay != null || after.cardInPlay != null

        val hands = after.players.mapNotNull { seat ->
            val was = before.players.firstOrNull { it.id == seat.id }?.cards?.size
            if (was != null && was != seat.cards.size) "${seat.id} ${was}→${seat.cards.size}" else null
        }

        val pile = after.discardCount - before.discardCount
        val deck = before.drawPileSize - after.drawPileSize
        val pending = (before.pendingAction == null) != (after.pendingAction == null)

        val notes = buildList {
            addAll(hands)
            if (pile != 0 && !settled && !taken) add("pile $pile")
            if (deck != 0) add("deck $deck")
            if (pending && !onThePile) add("pending changed")
        }
        return notes.takeIf { it.isNotEmpty() }?.joinToString()
    }

    private companion object {
        const val TURNS = 12
        const val MANY = 10
    }
}
