package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.protocol.LoggedAction
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Sha256
import game.vinto.shapes.TableTalk
import game.vinto.shapes.actorId
import kotlin.random.Random

/**
 * The doors the coalition speaks through, and what the room makes of what it hears.
 *
 * Split out of `RoomCore` because they are one subject rather than five: a sentence, who may
 * say it, what a bot makes of it, and when the table stops talking and plays. `RoomCore` is
 * the room's lifecycle — seats, clocks, deals, the driver — and this is the conversation that
 * happens inside one.
 *
 * Nothing here is game state (design D6). Talk is never reduced, never recorded and never near
 * a hash; the one thing that *is* — a claim — travels as an ordinary `DECLARE_CARDS` through
 * the engine like any other action.
 */

/**
 * One sentence of table talk, checked and passed on.
 *
 * Three rules, and the first two are the ones an action goes through:
 *
 *  - the **seat is derived from the token**, never asserted beside it, so there is no pair of
 *    credentials to play off against each other;
 *  - a seat **speaks only as itself** — the same rule `actorId` gets, because a phrasebook
 *    that let one seat put words in another's mouth would be worse than a chat box, not
 *    better;
 *  - it **spends from the same budget an action does**. Talk is cheap to send and cheap to
 *    serve, but it is broadcast to every socket, so an uncapped one is a flood with extra
 *    steps. Sharing the bucket also means a seat cannot talk its way out of its move
 *    allowance, which is the honest accounting: both cost the room a fan-out.
 *
 * Nothing here touches the game. Talk is not state (design D6): it is never reduced, never
 * recorded, and never near a hash.
 */
internal fun say(state: RoomState, token: String, talk: TableTalk, nowMs: Double): Spoken {
    val seatEntry = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return Spoken(state, error = NO_SEAT_FOR_TOKEN)

    val spend = spendBudget(state, seatEntry.index, nowMs)
    spend.retryAfterMs?.let {
        return Spoken(spend.state, error = "too much talking", retryAfterMs = it)
    }

    if (talk.by != seatEntry.playerId) {
        return Spoken(spend.state, error = "seat ${seatEntry.index} may only speak as ${seatEntry.playerId}")
    }
    return Spoken(spend.state, talk = talk)
}

/**
 * A bot's answer to a suggestion addressed to it, and the move if it agreed.
 *
 * The same thing `LocalGameSession` does, in the same place in the flow, so a person talking
 * to a bot gets the same game whichever session they are in. The move a bot makes when it
 * agrees is **its own**, through `ActionValidator` and `GameEngine.reduce` like any other —
 * a proposal never becomes the proposer's move.
 *
 * Only for a seat the room is actually playing. A suggestion to another *person* is simply
 * relayed and answered by them, if they choose to.
 */
internal fun answerFromBots(state: RoomState, proposal: TableTalk.Proposal): PlayedOut {
    val game = state.game ?: return PlayedOut(state, emptyList())
    val seat = state.seats.firstOrNull { it.playerId == proposal.to }
    val playedByAPerson = seat != null && seat.tokenHash != null && !seat.isBot
    if (seat == null || playedByAPerson) return PlayedOut(state, emptyList())

    val runner = BotRunner(state.difficulty, Random(state.seed))
    val (move, answer) = runner.answerTo(asPlayed(game, state.seats), proposal)
    val accepted = move ?: return PlayedOut(state, emptyList(), listOf(answer))

    if (ActionValidator.validate(game, accepted) is Validation.Invalid) {
        return PlayedOut(state, emptyList(), listOf(answer))
    }
    val reduced = GameEngine.reduce(game, accepted) as? ReduceResult.Success
        ?: return PlayedOut(state, emptyList(), listOf(answer))

    val entry = LoggedAction(
        index = state.nextIndex,
        seat = seat.index,
        playerId = accepted.actorId ?: "",
        action = accepted,
        byBot = true,
    )
    // `nextIndex` is derived from the log's size, so appending the entry advances it.
    val next = state.copy(game = reduced.state, log = state.log + entry)
    val played = playBotsTracked(next)
    return PlayedOut(
        played.state,
        listOf(Step(entry, reduced.state, reduced.revealed)) + played.steps,
        listOf(answer) + played.said,
    )
}

/**
 * Marks the window done for this round, so it opens once rather than once per action.
 *
 * The ready list goes with it: the next round's window starts with nobody having said
 * anything, which is what a new conversation is.
 */
internal fun closeConfer(state: RoomState): RoomState = state.copy(
    conferUntilEpochMs = null,
    conferReady = emptyList(),
    conferredRound = state.game?.roundNumber,
)

/**
 * "I have said what I wanted to say."
 *
 * The window closes the moment every **connected** coalition member has said it, so three
 * people who agree in five seconds are not held for twenty. Somebody who drops mid-window
 * stops being waited for, which is the same rule the toss-in uses and for the same reason: a
 * table must never be held by a seat nobody is sitting in.
 */
internal fun doneConferring(state: RoomState, token: String): Spoken {
    val seatEntry = state.seats.firstOrNull { it.tokenHash == Sha256.hex(token) }
        ?: return Spoken(state, error = NO_SEAT_FOR_TOKEN)
    if (!conferring(state)) return Spoken(state, error = "nobody is conferring")
    if (seatEntry.index !in conferringHumans(state)) {
        return Spoken(state, error = "the coalition is not waiting on you")
    }

    val ready = (state.conferReady + seatEntry.index).distinct()
    val everyone = conferringHumans(state)
    return Spoken(
        if (everyone.all { it in ready }) closeConfer(state) else state.copy(conferReady = ready),
    )
}

/**
 * Locks the lane of whoever is on play, and leaves the rest open.
 *
 * A plan must not change under the hand of the person executing it — the step they agreed to
 * is the step they are acting on. Later lanes stay editable, because the round is still going
 * and better information keeps arriving; freezing the whole plan at the first turn would make
 * every reveal after it unusable.
 *
 * Once locked, a lane stays locked. A turn does not un-begin.
 */
internal fun RoomState.withLanesLocked(plan: CoalitionPlan): CoalitionPlan {
    val onPlay = game?.let { it.players.getOrNull(it.currentPlayerIndex)?.id } ?: return plan
    return plan.copy(
        lanes = plan.lanes.map { lane ->
            if (lane.seat == onPlay) lane.copy(locked = true) else lane
        },
    )
}

/**
 * The coalition seats a person is actually sitting in, connected right now.
 *
 * A window for nobody is a pause for nobody: with an all-bot coalition — or one whose people
 * have all gone — there is nothing to confer and the round starts at once.
 */
internal fun conferringHumans(state: RoomState): List<Int> {
    val game = state.game ?: return emptyList()
    if (game.phase != GamePhase.FINAL) return emptyList()
    val caller = game.vintoCallerId ?: return emptyList()
    return state.seats
        .filter { it.tokenHash != null && !it.isBot && it.index in state.connectedSeats }
        .filter { it.playerId != null && it.playerId != caller }
        .map { it.index }
}

/**
 * Whether the final round is holding for the coalition to confer.
 *
 * Internal rather than private because it is the question, and a harness standing in for
 * people has to ask the same one the room does. Approximating it with the clock is how a
 * driver ends up trying to close a window that is not open, or missing one that is.
 */
internal fun conferring(state: RoomState): Boolean {
    val game = state.game ?: return false
    return game.roundNumber != state.conferredRound && conferringHumans(state).isNotEmpty()
}
