package game.vinto.client

import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.actorId
import game.vinto.shapes.retired
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Whole solo games, played through the table, offer nothing the engine refuses.
 *
 * `NothingOnOfferIsRefusedTest` holds the one state the first `MOVE_REFUSED in SOLO` came from.
 * The second arrived from the web on 2026-09-28 with no state at all — a refusal is reported by
 * its rate and never its reason, because the reason is free text written for a person. So this
 * does not wait for the next report to say where: it plays games the way a person does, from
 * nothing but what the table offers, and at every step runs **everything** on offer — buttons,
 * card taps, seats, ranks — through the same three checks `LocalGameSession.dispatch` makes
 * before the engine sees it. Then it touches one of them at random and goes on.
 *
 * What it found first: in the final round, a coalition member's 9, 10, Jack, Queen and Ace were
 * all offered the Vinto caller's cards, which the engine refuses to the coalition.
 *
 * JVM only: a dozen whole games with three searching bots is most of a minute here and a good
 * deal more on Kotlin/JS, and the property is about the table model, not about a target.
 */
class AWholeGameOffersNothingRefusedTest {

    @Test
    fun everythingATableOffersInAWholeGameIsAMoveTheEngineTakes() = runTest(timeout = 5.minutes) {
        val games = (1L..GAMES).map { seed -> play(seed) }

        assertEquals(
            emptyList(),
            games.flatMap { it.refused }.distinctBy { it.substringAfter(" — ") }.take(SHOWN),
            "the table offered a move the engine refuses, which is the tap that does nothing",
        )
        // The final round is where the coalition's rules live, and where this found its first
        // refusal. A walk that stopped reaching it would pass by looking at nothing.
        assertTrue(
            games.count { it.reachedTheFinalRound } >= GAMES / 2,
            "only ${games.count { it.reachedTheFinalRound }} of $GAMES games reached the final round",
        )
    }

    private class Game(val refused: List<String>, val reachedTheFinalRound: Boolean)

    private suspend fun play(seed: Long): Game {
        val session = LocalGameSession(seed = seed)
        val touch = Random(seed * PICK_SALT + 1)
        var question: Question = Question.None
        val refused = mutableListOf<String>()
        var final = false

        for (step in 0 until STEPS) {
            if (session.isOver) break
            val view = session.view.value
            if (view.phase == GamePhase.FINAL) final = true
            val table = tableFor(view, question, session.away.value, null, session.plan.value, session.reveals.value)
            val offers = offersOn(table)
            val where = "game $seed step $step, ${view.phase}/${view.subPhase}, asking $question"

            refused += refusedAmong(session, offers).map { "$where — offered $it" }
            if (offers.isEmpty()) break

            val (what, move) = pick(offers, step, touch)
            val (answer, next) = touch(session, move, question)
            question = next
            answer?.let { refused += "$where — touched \"$what\": $it" }
        }
        return Game(refused, final)
    }

    private fun refusedAmong(session: LocalGameSession, offers: List<Pair<String, Move>>): List<String> =
        offers.mapNotNull { (what, move) ->
            (move as? Move.Send)?.action?.let { refusalOf(session, it) }?.let { "\"$what\": $it" }
        }

    /**
     * One offer, at random. Calling ends the round, and a random finger calls at once — so it is
     * held back until the round has run long enough to reach its final turns: a bot calls, and
     * the plan opens.
     */
    private fun pick(offers: List<Pair<String, Move>>, step: Int, touch: Random): Pair<String, Move> {
        val reachable = offers.filter {
            step >= CALL_FROM || (it.second as? Move.Send)?.action !is GameAction.CallVinto
        }
        return reachable.ifEmpty { offers }.let { it[touch.nextInt(it.size)] }
    }

    /** What touching [move] does, as `GameHolder.act` does it: the refusal, and the question after. */
    private suspend fun touch(session: LocalGameSession, move: Move, question: Question): Pair<String?, Question> {
        val answer = when (move) {
            is Move.Ask -> return null to move.question
            is Move.Send -> session.dispatch(move.action)
            is Move.Say -> session.say(move.talk)
            Move.Done -> session.doneConferring()
            is Move.Plan -> return session.editPlan(move.edit) to question
            is Move.Agree -> return session.agreePlan(move.agree) to question
        }
        return answer to if (answer == null) Question.None else question
    }

    /** Everything on the table a finger can reach, each with what it says. */
    private fun offersOn(table: Table): List<Pair<String, Move>> =
        table.choices.map { "${it.label}" to it.move } +
            table.taps.map { (card, move) -> "the card ${card.playerId}:${card.position}" to move } +
            table.seats.map { "the seat ${it.id}" to it.move } +
            table.ranks.map { "the rank ${it.rank}" to it.move }

    /** The three refusals `LocalGameSession.dispatch` can give, asked without dispatching. */
    private fun refusalOf(session: LocalGameSession, action: GameAction): String? = when {
        action.retired -> "retired"
        action.actorId != null && action.actorId != session.playerId -> "acts for ${action.actorId}"
        else -> (ActionValidator.validate(session.state, action) as? Validation.Invalid)?.reason
    }

    private companion object {
        const val GAMES = 12L
        const val STEPS = 500
        const val SHOWN = 12
        const val PICK_SALT = 31L
        const val CALL_FROM = 120
    }
}
