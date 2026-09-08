package game.vinto.room

import game.vinto.engine.projectView
import game.vinto.protocol.EventEntry
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RevealedCard
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.TableTalk
import game.vinto.shapes.VintoJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * The room's messages, built where the rules are.
 *
 * Until now the core returned *results* — state plus a list of `LoggedAction` — and
 * `index.mjs` assembled the wire messages itself, per socket, in JavaScript. That worked
 * while a message was a list of public events beside one final view; it stops working the
 * moment events carry **per-event views** (choreography change 4.1), because those exist
 * per step and per seat, and only the code holding every intermediate `GameState` can build
 * them. That code is here, so the messages are built here: one serializer on both ends of
 * the wire, and the JavaScript narrows to sending strings it does not read.
 *
 * Everything returns per-seat **message strings**, keyed by seat index; `index.mjs` looks up
 * each socket's seat and sends the string as-is.
 *
 * Cost: one `projectView` + one serialization per step per seat. Bounded by the same things
 * that bound the work itself — `MAX_BOT_STEPS` above and the rate limiter in front — and
 * noted in `PROTOCOL.md`.
 */

/** Per-seat wire messages beside the state they describe, or a refusal. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class Envelopes(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val retryAfterMs: Double? = null,
    /** Seat index → the exact JSON text to send that seat's socket. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val messages: Map<Int, String> = emptyMap(),
)

/** [Envelopes] plus the lifecycle facts the alarm handler acts on. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class AlarmEnvelopes(
    val state: RoomState,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val nextAlarmAtEpochMs: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val started: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tookOver: List<Int> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val messages: Map<Int, String> = emptyMap(),
)

/**
 * [applyAction], with the wire messages built alongside: an `events` message per seat, each
 * entry carrying that seat's view after that step. Refusals come back as [Envelopes.error]
 * with no messages — the refusal goes to the sender alone, and `index.mjs` owns that send.
 */
fun applyActionEnvelopes(stateJson: String, token: String, actionJson: String, nowMs: Double): String {
    val applied = applyActionApplied(stateJson, token, actionJson, nowMs)
    if (applied.error != null) {
        return VintoJson.encodeToString(
            Envelopes(applied.state, error = applied.error, retryAfterMs = applied.retryAfterMs),
        )
    }
    return VintoJson.encodeToString(
        Envelopes(
            applied.state,
            messages = eventsPerSeat(applied.state, applied.steps, nowMs, applied.said),
        ),
    )
}

/**
 * [readyForNextRound], with per-seat `started` / `between-rounds` messages. The deal is a
 * baseline rather than a trail — a client lands on the new view and a fresh cursor — so no
 * per-event entries are needed here.
 */
fun readyEnvelopes(stateJson: String, token: String, nowMs: Double): String {
    val result = VintoJson.decodeFromString(
        JoinResult.serializer(),
        readyForNextRound(stateJson, token, nowMs),
    )
    if (result.error != null) {
        return VintoJson.encodeToString(Envelopes(result.state, error = result.error))
    }

    val state = result.state
    val remaining = remainingMs(state, nowMs)
    val messages = seated(state).associate { (seatIndex, playerId) ->
        val view = state.game?.let {
            projectView(it, playerId, remaining, tossInMsLeft(state, nowMs), conferMsLeft(state, nowMs))
        }
        val message = if (state.phase == RoomPhase.PLAYING) {
            ServerMessage.Started(view, state.nextIndex, standings = state.session.rounds)
        } else {
            ServerMessage.BetweenRounds(view, standings = state.session.rounds, nextIndex = state.nextIndex)
        }
        seatIndex to ProtocolJson.encodeToString(ServerMessage.serializer(), message)
    }
    return VintoJson.encodeToString(Envelopes(state, messages = messages))
}

/**
 * [onAlarm], with whatever messages its outcome calls for: `started` per seat when the
 * countdown dealt, `events` per seat when a grace expired and bots played — with per-event
 * views, exactly as a client action's trail gets. The deleted / lonely / buzzer outcomes
 * carry no messages; their broadcasts (`closed`, `ended`) hold no view and stay with the
 * JavaScript that owns the sockets.
 */
fun alarmEnvelopes(stateJson: String, nowMs: Double): String {
    val tracked = onAlarmTracked(stateJson, nowMs)
    val result = tracked.result
    val state = result.state
    val remaining = remainingMs(state, nowMs)

    val messages = when {
        result.started -> seated(state).associate { (seatIndex, playerId) ->
            val view = state.game?.let {
                projectView(it, playerId, remaining, tossInMsLeft(state, nowMs), conferMsLeft(state, nowMs))
            }
            seatIndex to ProtocolJson.encodeToString(
                ServerMessage.serializer(),
                ServerMessage.Started(view, state.nextIndex),
            )
        }

        // The log grew ⇒ send events. Which alarm did the growing — a seat-grace takeover,
        // a pacing expiry — is the room's business; what a client needs is the same either
        // way: the actions, each with its view.
        tracked.steps.isNotEmpty() -> eventsPerSeat(state, tracked.steps, nowMs, tracked.said)

        else -> emptyMap()
    }

    return VintoJson.encodeToString(
        AlarmEnvelopes(
            state = state,
            nextAlarmAtEpochMs = result.nextAlarmAtEpochMs,
            deleted = result.deleted,
            started = result.started,
            tookOver = result.tookOver,
            messages = messages,
        ),
    )
}

/**
 * One `sync` message for one socket: the log from the cursor, and — so a reconnector lands
 * on the present rather than on its stale last view — the seat's current view. The catch-up
 * entries carry no per-event views: the room keeps no past states (that is what makes
 * hibernation safe), so a client jumps the cursor and renders the view. A seat of -1 (a
 * socket that never joined) gets the public log and no view at all.
 */
fun syncEnvelope(stateJson: String, seat: Int, sinceIndex: Int, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val from = sinceIndex.coerceIn(0, state.log.size)
    val playerId = state.seats.getOrNull(seat)?.playerId
    val view = playerId?.let { id ->
        state.game?.let {
            projectView(
                it,
                id,
                remainingMs(state, nowMs),
                tossInMsLeft(state, nowMs),
                conferMsLeft(state, nowMs),
            )
        }
    }

    return ProtocolJson.encodeToString(
        ServerMessage.serializer(),
        ServerMessage.Sync(
            events = state.log.drop(from).map { entry ->
                EventEntry(entry.index, entry.seat, entry.playerId, entry.action, entry.byBot)
            },
            nextIndex = state.nextIndex,
            view = view,
            away = awayPlayerIds(state),
            plan = state.plan,
        ),
    )
}

/**
 * The seats a bot is playing on an absent person's behalf, as engine player ids.
 *
 * A seat that is a bot *and* still holds a token is somebody's seat being covered; a seat with
 * no token is filler, which is nobody's and needs no label. The distinction is `Seat.isFiller`'s
 * and it is the same one the lobby draws on.
 */
private fun awayPlayerIds(state: RoomState): List<String> =
    state.seats.filter { it.isBot && it.tokenHash != null }.mapNotNull { it.playerId }

/** An `events` message per seated seat, each entry carrying that seat's view of its step. */
private fun eventsPerSeat(
    state: RoomState,
    steps: List<Step>,
    nowMs: Double,
    said: List<TableTalk> = emptyList(),
): Map<Int, String> {
    val remaining = remainingMs(state, nowMs)
    val tossLeft = tossInMsLeft(state, nowMs)
    val conferLeft = conferMsLeft(state, nowMs)
    val away = awayPlayerIds(state)
    return seated(state).associate { (seatIndex, playerId) ->
        val entries = steps.map { step ->
            EventEntry(
                index = step.logged.index,
                seat = step.logged.seat,
                playerId = step.logged.playerId,
                action = step.logged.action,
                byBot = step.logged.byBot,
                view = projectView(step.after, playerId, remaining, tossLeft, conferLeft),
                revealed = step.revealed.map { RevealedCard(it.playerId, it.position, it.card) },
            )
        }
        // The top-level view is where the batch *ends* — after settling, so a FINISHED room
        // sends its trail with a null destination and the client falls back to the entries.
        val view = state.game?.let { projectView(it, playerId, remaining, tossLeft, conferLeft) }
        seatIndex to ProtocolJson.encodeToString(
            ServerMessage.serializer(),
            ServerMessage.Events(
                events = entries,
                nextIndex = state.nextIndex,
                view = view,
                away = away,
                said = said,
                // On every batch, not only on an edit: a lane locks when its owner's turn
                // begins, which is an ordinary action's doing, and the table has to see it.
                plan = state.plan,
            ),
        )
    }
}

/**
 * One part of the shared plan, changed, and the board sent back to every seat.
 *
 * Answered the way `more-time` is: an empty `events` message per seat, whose `plan` is the
 * whole resulting board and whose `said` carries the answer of the bot whose lane was set.
 * There is no `planned` message to add to the wire, and a client already knows how to take a
 * plan off an `events`.
 */
fun editPlanEnvelopes(stateJson: String, token: String, editJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val edit = try {
        ProtocolJson.decodeFromString(PlanEdit.serializer(), editJson)
    } catch (failure: IllegalArgumentException) {
        return VintoJson.encodeToString(
            Envelopes.serializer(),
            Envelopes(state, error = "unreadable plan edit: ${failure.message}"),
        )
    }

    val spoken = editPlan(state, token, edit, nowMs)
    if (spoken.error != null) {
        return VintoJson.encodeToString(
            Envelopes.serializer(),
            Envelopes(spoken.state, error = spoken.error, retryAfterMs = spoken.retryAfterMs),
        )
    }
    return VintoJson.encodeToString(
        Envelopes.serializer(),
        Envelopes(
            spoken.state,
            messages = eventsPerSeat(spoken.state, emptyList(), nowMs, listOfNotNull(spoken.talk)),
        ),
    )
}

/**
 * One member's yes or no to the plan as a whole.
 *
 * A yes that closes the confer window plays the seats it was holding, exactly as
 * [doneConferringEnvelopes] does; any other answer moves nothing and is broadcast as an empty
 * `events` carrying the board.
 */
fun agreePlanEnvelopes(stateJson: String, token: String, agree: Boolean, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val said = agreePlan(state, token, agree)
    if (said.error != null) {
        return VintoJson.encodeToString(Envelopes(said.state, error = said.error))
    }

    val closedTheWindow = conferring(state) && !conferring(said.state)
    val played = if (closedTheWindow) playBotsTracked(said.state) else PlayedOut(said.state, emptyList())
    val settled = withPacing(played.state, nowMs, watching = played.steps.size)
    return VintoJson.encodeToString(
        Envelopes(settled, messages = eventsPerSeat(settled, played.steps, nowMs, played.said)),
    )
}

/**
 * [moreTimeApplied], with the refreshed clock broadcast to every seat: an `events` message
 * with no entries, whose view carries the extended countdown. Nothing moved on the table —
 * there is nothing to animate — but every phone's clock has to jump together, or the table
 * disagrees about how long it is waiting.
 */
fun moreTimeEnvelopes(stateJson: String, token: String, nowMs: Double): String {
    val applied = moreTimeApplied(stateJson, token)
    if (applied.error != null) {
        return VintoJson.encodeToString(Envelopes(applied.state, error = applied.error))
    }
    return VintoJson.encodeToString(
        Envelopes(applied.state, messages = eventsPerSeat(applied.state, emptyList(), nowMs)),
    )
}

/** The seats that map to a player, as (seat index, player id) — the ones messages go to. */
private fun seated(state: RoomState): List<Pair<Int, String>> =
    state.seats.mapNotNull { seat -> seat.playerId?.let { seat.index to it } }

/** How long the coalition still has to confer, as a duration a phone can count down. */
private fun conferMsLeft(state: RoomState, nowMs: Double): Long? =
    state.conferUntilEpochMs?.let { maxOf(0.0, it - nowMs).toLong() }

/** The session clock as a view carries it; the projection never reads one itself. */
private fun remainingMs(state: RoomState, nowMs: Double): Long? =
    state.session.endsAtEpochMs?.let { maxOf(0.0, it - nowMs).toLong() }

/** The toss-in clock as a duration, for the same reason: a phone's own clock may be wrong. */
private fun tossInMsLeft(state: RoomState, nowMs: Double): Long? =
    state.tossInDeadlineEpochMs?.let { maxOf(0.0, it - nowMs).toLong() }

/**
 * One sentence, checked and turned into a message for every seated socket.
 *
 * Broadcast to **everyone**, the Vinto caller included. That is faithful — the coalition
 * confers out loud at a table — and it costs them nothing, because the caller has already had
 * their turn and cannot act again. It is also better theatre: the one person the plan is
 * against gets to watch it being made.
 */
fun sayEnvelopes(stateJson: String, token: String, talkJson: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val talk = try {
        ProtocolJson.decodeFromString(TableTalk.serializer(), talkJson)
    } catch (failure: IllegalArgumentException) {
        return VintoJson.encodeToString(
            Envelopes.serializer(),
            Envelopes(state, error = "unreadable talk: ${failure.message}"),
        )
    }

    val spoken = say(state, token, talk, nowMs)

    // A suggestion addressed to a seat the room plays is answered by that bot, with the move
    // it makes if it agrees — the same thing a solo game does, in the same place in the flow,
    // so a person talking to a bot gets the same game whichever session they are in.
    val answered = (spoken.talk as? TableTalk.Proposal)?.let { answerFromBots(spoken.state, it) }
    if (answered != null && (answered.steps.isNotEmpty() || answered.said.isNotEmpty())) {
        val settled = withPacing(answered.state, nowMs, watching = answered.steps.size)
        // One message per seat, so the suggestion travels in the same `said` list as the
        // answer and the moves rather than as a second message the events would overwrite.
        return VintoJson.encodeToString(
            Envelopes.serializer(),
            Envelopes(
                state = settled,
                messages = eventsPerSeat(
                    settled,
                    answered.steps,
                    nowMs,
                    listOf(talk) + answered.said,
                ),
            ),
        )
    }

    val said: Map<Int, String> = spoken.talk?.let { sentence ->
        val text = ProtocolJson.encodeToString(
            ServerMessage.serializer(),
            ServerMessage.Said(sentence),
        )
        seated(spoken.state).associate { (seatIndex, _) -> seatIndex to text }
    }.orEmpty()

    return VintoJson.encodeToString(
        Envelopes.serializer(),
        Envelopes(
            state = spoken.state,
            error = spoken.error,
            retryAfterMs = spoken.retryAfterMs,
            messages = said,
        ),
    )
}

/**
 * One coalition member saying they have finished conferring.
 *
 * When that closes the window, the seats it was holding are played at once — closing is not
 * itself a move, and a table that stopped talking should not then sit waiting for a clock.
 */
fun doneConferringEnvelopes(stateJson: String, token: String, nowMs: Double): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)
    val said = doneConferring(state, token)
    if (said.error != null) {
        return VintoJson.encodeToString(Envelopes(said.state, error = said.error))
    }

    val played = playBotsTracked(said.state)
    val settled = withPacing(played.state, nowMs, watching = played.steps.size)
    return VintoJson.encodeToString(
        Envelopes(settled, messages = eventsPerSeat(settled, played.steps, nowMs, played.said)),
    )
}
