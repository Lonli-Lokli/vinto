package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.RoundResult
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.VintoJson
import game.vinto.shapes.actorId
import kotlin.random.Random

/**
 * The plumbing every room suite needs, written once.
 *
 * The style is the one `index.mjs` imposes and `RoomCoreTest` established: JSON strings in,
 * JSON strings out, the clock passed as an argument. What is added here is the room every
 * scenario starts from — two people seated, two filler bots, the countdown expired — with
 * **presence recorded**, because that is what a real room has: `newRoom` puts an empty room
 * on the deletion clock, and only a presence update takes it off. A dealt room whose sockets
 * were never reported is a room the next alarm deletes, which is not the room any of these
 * tests means to be talking about.
 */

internal const val START = 1_000_000.0
internal const val LATER = START + 20_000.0
internal const val TOKEN_A = "token-ann"
internal const val TOKEN_B = "token-bob"
internal const val STRANGER = "token-mallory"

/** `RoomCore`'s private deadlines, pinned. A drift there fails the test that reads it. */
internal const val SEAT_GRACE_MS = 30_000.0
internal const val LONELY_GRACE_MS = 60_000.0
internal const val ROOM_TTL_MS = 120_000.0
internal const val FINISHED_TTL_MS = 600_000.0

internal fun encode(state: RoomState): String =
    VintoJson.encodeToString(RoomState.serializer(), state)

internal fun decodeRoom(json: String): RoomState =
    VintoJson.decodeFromString(RoomState.serializer(), json)

internal fun decodeJoin(json: String): JoinResult =
    VintoJson.decodeFromString(JoinResult.serializer(), json)

internal fun decodeAction(json: String): ActionResult =
    VintoJson.decodeFromString(ActionResult.serializer(), json)

internal fun decodeLifecycle(json: String): LifecycleResult =
    VintoJson.decodeFromString(LifecycleResult.serializer(), json)

internal fun decodeEnvelopes(json: String): Envelopes =
    VintoJson.decodeFromString(Envelopes.serializer(), json)

internal fun decodeAlarm(json: String): AlarmEnvelopes =
    VintoJson.decodeFromString(AlarmEnvelopes.serializer(), json)

internal fun decodeView(json: String): ViewResult =
    VintoJson.decodeFromString(ViewResult.serializer(), json)

internal fun actionJson(action: GameAction): String =
    VintoJson.encodeToString(GameAction.serializer(), action)

/** Ann and Bob seated, both sockets open, two seats nobody has filled. */
internal fun lobbyOfTwo(now: Double = START, seed: Double = 42.0): String {
    var state = newRoom("room-TEST", seed = seed, difficulty = "easy", nowMs = now)
    state = encode(decodeJoin(joinRoom(state, TOKEN_A, "Ann", now)).state)
    state = encode(decodeJoin(joinRoom(state, TOKEN_B, "Bob", now)).state)
    return encode(decodeLifecycle(updatePresence(state, "0,1", now)).state)
}

/** [lobbyOfTwo] with two filler bots and the countdown run out: the game is dealt. */
internal fun dealtRoom(now: Double = START, seed: Double = 42.0): String {
    var state = lobbyOfTwo(now, seed)
    state = encode(decodeJoin(addBot(state, TOKEN_A, now)).state)
    state = encode(decodeJoin(addBot(state, TOKEN_A, now)).state)
    return encode(decodeLifecycle(onAlarm(state, now + countdownMs() + 1)).state)
}

/**
 * A dealt room whose round has been scored and filed, waiting on the next-round agreement.
 *
 * Built the way `settleRound` leaves a room: the game kept on the table in `scoring`, the
 * round on the session, `roundFinal` holding where it ended. The hands are the dealt ones —
 * what a round's totals *are* is the engine's business and not what these tests ask.
 */
internal fun betweenRounds(dealtJson: String): RoomState {
    val room = decodeRoom(dealtJson)
    val game = checkNotNull(room.game) { "a dealt room has a game" }.copy(phase = GamePhase.SCORING)
    val filed = RoundResult(
        roundNumber = 1,
        vintoCallerId = null,
        scores = calculateFinalScores(game.players, null),
        points = calculateRoundPoints(game.players, null),
    )
    return room.copy(
        phase = RoomPhase.BETWEEN_ROUNDS,
        game = game,
        roundFinal = game,
        session = room.session.copy(rounds = listOf(filed)),
    )
}

/**
 * Plays a dealt room's round to its end through `applyAction`, the humans' seats decided by
 * the bots' own brain and every move authorised by its seat's token — `RoomRecordingTest`'s
 * driver, for any suite that needs a round to actually finish.
 */
internal fun playRoundOut(dealtJson: String, seed: Long, from: Double): String {
    var state = dealtJson
    val person = BotRunner(Difficulty.EASY, Random(seed))
    var now = from

    repeat(MOVE_LIMIT) {
        val room = decodeRoom(state)
        if (room.phase != RoomPhase.PLAYING) return state
        val game = room.game ?: return state

        val everySeat = game.copy(players = game.players.map { it.copy(isHuman = false, isBot = true) })
        val action = person.nextAction(everySeat) ?: return state
        // Whose token authorises it: the actor's seat when the action names one, and any
        // seated human for the actorless ones — choosing a coalition leader names nobody.
        val actor = action.actorId
        val token = if (actor == null) {
            TOKEN_A
        } else {
            val seat = room.seats.first { it.playerId == actor }
            check(seat.tokenHash != null) { "between requests the wanted move is a human's" }
            if (seat.index == 0) TOKEN_A else TOKEN_B
        }

        now += MS_BETWEEN_MOVES
        val result = decodeAction(applyAction(state, token, actionJson(action), now))
        check(result.error == null) { "move refused: ${result.error}" }
        state = encode(result.state)
    }
    return state
}

private const val MOVE_LIMIT = 600
private const val MS_BETWEEN_MOVES = 2_000.0
