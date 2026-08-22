package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapCardPayload
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where every card animation starts and ends, read out of the game rather than written down.
 *
 * The table this prints is the answer to "what should I see when I do X", and it is generated
 * by *doing* X: each case plays the move through a real session and reports the flights the
 * choreography produced. A hand-written table would be a description of what somebody
 * intended; this one cannot disagree with the app, because it is the app.
 *
 * Run `./gradlew :shared:client:jvmTest --tests "*AnimationMapTest*" -i` and the table is in
 * the output. `docs/kotlin/ANIMATIONS.md` is a copy of it.
 *
 * The assertions are the rows worth defending — the ones that have been wrong at least once.
 */
class AnimationMapTest {

    @Test
    fun theMapOfEveryMove() = runTest {
        val rows = mutableListOf<Row>()

        rows += flightsFor("Draw a card") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        }

        rows += flightsFor("Play the drawn card's action") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            mark(session)
            session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        }

        rows += flightsFor("Throw the drawn card away") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            mark(session)
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        }

        rows += flightsFor("Swap it in, saying nothing") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            mark(session)
            session.dispatch(GameAction.SwapCard(SwapCardPayload(me, SLOT)))
        }

        rows += flightsFor("Swap it in, calling the rank right") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            val called = session.state.players.first { it.id == me }.cards[SLOT].rank
            mark(session)
            session.dispatch(GameAction.SwapCard(SwapCardPayload(me, SLOT, called)))
        }

        rows += flightsFor("Swap it in, calling the rank wrong") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            val real = session.state.players.first { it.id == me }.cards[SLOT].rank
            val wrong = Rank.entries.first { it != real }
            mark(session)
            session.dispatch(GameAction.SwapCard(SwapCardPayload(me, SLOT, wrong)))
        }

        rows += flightsFor("Aim a peek (7, 8, 9, 10)") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
            mark(session)
            session.dispatch(selectOwn(me, SLOT))
        }

        rows += flightsFor("Finish looking") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
            session.dispatch(selectOwn(me, SLOT))
            mark(session)
            session.dispatch(GameAction.ConfirmPeek(PlayerIdPayload(me)))
        }

        rows += flightsFor("Name a rank with a King") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
            session.dispatch(selectOwn(me, SLOT))
            val real = session.state.players.first { it.id == me }.cards[SLOT].rank
            mark(session)
            session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(me, real)))
        }

        rows += flightsFor("Throw a card in") { session, me ->
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            val match = session.state.players.first { it.id == me }.cards
                .indexOfFirst { it.rank == Rank.FIVE }
            mark(session)
            if (match >= 0) {
                session.dispatch(
                    GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(match))),
                )
            }
        }

        println(table(rows))
        check(rows)
    }

    /** The rows worth defending: each of these has been wrong at least once. */
    private fun check(rows: List<Row>) {
        val map = rows.associateBy { it.what }
        assertEquals("deck → drawn slot", map.getValue("Draw a card").flights)
        assertEquals("drawn slot → discard (lit)", map.getValue("Play the drawn card's action").flights)
        assertEquals("drawn slot → discard", map.getValue("Throw the drawn card away").flights)
        assertEquals(
            "drawn slot → your hand, your hand → discard",
            map.getValue("Swap it in, saying nothing").flights,
        )
        assertEquals(
            "drawn slot → your hand, your hand → discard (lit)",
            map.getValue("Swap it in, calling the rank right").flights,
            "a called card goes to the pile like any other — its action is simply played there",
        )
        assertEquals("nothing moves", map.getValue("Aim a peek (7, 8, 9, 10)").flights)
        assertEquals("nothing moves", map.getValue("Finish looking").flights)
    }

    // ------------------------------------------------------------------ the reading

    private data class Row(val what: String, val flights: String)

    private var watching = false

    private fun mark(session: LocalGameSession) {
        watching = true
        session.hashCode()
    }

    private suspend fun flightsFor(
        what: String,
        play: suspend (LocalGameSession, String) -> Unit,
    ): Row {
        val session = LocalGameSession(seed = 21L, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        val seen = mutableListOf<Frame>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val job = collector.launch { session.frames.collect { seen += it } }

        watching = false
        play(session, me)
        job.cancel()

        // The move being described is the last thing the player did; the frames before it are
        // the set-up this case needed.
        val mine = seen.lastOrNull { it.actorId == me }
        val moves = mine?.scenes?.flatten()?.filterIsInstance<Beat.Move>().orEmpty()

        return Row(
            what = what,
            flights = if (moves.isEmpty()) {
                "nothing moves"
            } else {
                moves.joinToString { move ->
                    "${place(move.from, me)} → ${place(move.to, me)}" + if (move.shown) " (lit)" else ""
                }
            },
        )
    }

    private fun place(anchor: Anchor, me: String) = when (anchor) {
        Anchor.Deck -> "deck"
        Anchor.Discard -> "discard"
        Anchor.Pending -> "drawn slot"
        is Anchor.Seat -> if (anchor.playerId == me) "your hand" else "their hand"
    }

    private fun selectOwn(me: String, position: Int) = GameAction.SelectActionTarget(
        game.vinto.shapes.SelectActionTargetPayload.Positional(me, me, position),
    )

    private fun table(rows: List<Row>): String {
        val width = rows.maxOf { it.what.length }
        return buildString {
            appendLine()
            appendLine("| ${"What you do".padEnd(width)} | What flies, and where |")
            appendLine("| ${"-".repeat(width)} | --------------------- |")
            rows.forEach { appendLine("| ${it.what.padEnd(width)} | ${it.flights} |") }
        }
    }

    private companion object {
        const val SLOT = 2
    }
}
