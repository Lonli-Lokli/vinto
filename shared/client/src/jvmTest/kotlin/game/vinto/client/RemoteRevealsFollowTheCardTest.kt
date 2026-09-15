package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.PublicReveal
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.engine.tossInIsOpen
import game.vinto.protocol.EventEntry
import game.vinto.protocol.RevealedCard
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.actorId
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A reveal follows its card over the wire as well.
 *
 * `RevealsFollowTheCardTest` holds this for the solo session, and holding it there proved
 * nothing about the online one. Both call the same `following`, so the helper was never the
 * question — the **wiring** is, and the two sessions wire it differently: the solo one owns the
 * state and can be asked what is true, while the online one owns nothing. A card turned face up
 * reaches it once, as a `RevealedCard` on an event, and after that it never sees a card's
 * identity again; whether a standing reveal still names the place its card lies is entirely a
 * property of `applyEvents`.
 *
 * So the room is played here by a real engine, one action at a time, and each event carries the
 * two things `RoomCore` puts on one: the watching seat's projected view, and the reveals off
 * `ReduceResult`. The person's seat throws a card it has not looked at into every window it may,
 * because that is the one move that turns a card face up on purpose — and the reveal then has to
 * follow it through every trade and every hand that closes up behind a throw.
 */
class RemoteRevealsFollowTheCardTest {

    @Test
    fun aRevealFollowsItsCardThroughWholeGamesOverTheWire() = runTest(timeout = WHOLE_GAME) {
        var stood = 0
        for (seed in SEEDS) {
            val wire = Wire(this)
            val table = Table(seed)
            wire.deliverJoined(projectView(table.state, table.me))
            wire.settle()
            val session = assertNotNull(wire.room.session.value, "a join with a view creates the session")

            var moves = 0
            while (table.state.phase != GamePhase.SCORING && moves++ < MOVE_LIMIT) {
                val step = table.step() ?: break
                wire.deliver(step)
                wire.settle()
                table.truth.check(table.state, session, "seed $seed after $moves moves")
                if (session.reveals.value.isNotEmpty()) stood++
            }

            assertEquals(GamePhase.SCORING, table.state.phase, "seed $seed never reached scoring")
            wire.room.leave()
        }
        assertTrue(stood > 0, "no reveal ever stood over the wire, so nothing was followed")
    }

    /** The room, played for real: the engine, the bots, and the envelope a step goes out in. */
    private class Table(private val seed: Long) {
        var state: GameState = initializeGame(seed, Difficulty.EASY)
            .let { dealt -> dealt.copy(players = dealt.players.map { it.copy(isHuman = false, isBot = true) }) }
            private set

        val me: String = state.players.first().id
        val truth = Truth()

        private val bots = BotRunner(Difficulty.EASY, Random(seed))
        private var index = 0
        private var threwInThisWindow = false

        /** One action, applied and wrapped as the room wraps it; null when nobody has a move. */
        fun step(): ServerMessage.Events? {
            val action = blindThrow() ?: bots.nextAction(state) ?: return null
            val before = state
            val reduced = GameEngine.reduce(before, action) as? ReduceResult.Success ?: return null
            state = reduced.state
            bots.observe(action, before, state)
            truth.saw(reduced.revealed)

            val actor = action.actorId
            val entry = EventEntry(
                index = index,
                seat = state.players.indexOfFirst { it.id == actor }.coerceAtLeast(0),
                playerId = actor ?: me,
                action = action,
                byBot = true,
                view = projectView(state, me),
                revealed = reduced.revealed.map { RevealedCard(it.playerId, it.position, it.card) },
            )
            return ServerMessage.Events(events = listOf(entry), nextIndex = ++index)
        }

        /**
         * The person's blind throw, once per window. Validated rather than assumed: the window
         * this seat may throw into is the engine's judgement, and a refused action would stop
         * the table rather than test it.
         */
        private fun blindThrow(): GameAction? {
            val view = projectView(state, me)
            if (!view.tossInIsOpen) {
                threwInThisWindow = false
                return null
            }
            if (threwInThisWindow || me in view.barredFromTossIn) return null
            val mine = state.players.first { it.id == me }
            val blind = mine.cards.indices.firstOrNull { it !in mine.knownCardPositions } ?: return null
            val throwing = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(blind)))
            if (ActionValidator.validate(state, throwing) is Validation.Invalid) return null
            threwInThisWindow = true
            return throwing
        }
    }

    /**
     * The engine's own answer, which no client has: every reveal the round reported, at the place
     * its card lies now **by identity**, and gone once the card has left every hand.
     */
    private class Truth {
        private var standing = listOf<PublicReveal>()

        fun saw(fresh: List<PublicReveal>) {
            standing = standing + fresh
        }

        fun check(state: GameState, session: GameSession, where: String) {
            standing = standing.mapNotNull { reveal ->
                state.players.firstNotNullOfOrNull { seat ->
                    val at = seat.cards.indexOfFirst { it.id == reveal.card.id }
                    if (at >= 0) PublicReveal(seat.id, at, reveal.card) else null
                }
            }
            assertEquals(
                standing.map { Triple(it.playerId, it.position, it.card.id) }.toSet(),
                session.reveals.value.map { Triple(it.playerId, it.position, it.card.id) }.toSet(),
                where,
            )
        }
    }

    private companion object {
        val SEEDS = listOf(11L, 12L)
        const val MOVE_LIMIT = 600
    }
}
