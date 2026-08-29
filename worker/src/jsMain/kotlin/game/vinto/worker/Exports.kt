package game.vinto.worker

import game.vinto.room.addBot as coreAddBot
import game.vinto.room.alarmEnvelopes as coreAlarmEnvelopes
import game.vinto.room.applyAction as coreApplyAction
import game.vinto.room.applyActionEnvelopes as coreApplyActionEnvelopes
import game.vinto.room.countdownMs as coreCountdownMs
import game.vinto.room.eventsSince as coreEventsSince
import game.vinto.room.forgetRoom as coreForgetRoom
import game.vinto.room.joinRoom as coreJoinRoom
import game.vinto.room.listPublicRooms as coreListPublicRooms
import game.vinto.room.lobbyView as coreLobbyView
import game.vinto.room.maxLiveRooms as coreMaxLiveRooms
import game.vinto.room.maxRoomsPerSource as coreMaxRoomsPerSource
import game.vinto.room.mintRoomCode as coreMintRoomCode
import game.vinto.room.newRegistry as coreNewRegistry
import game.vinto.room.newRoom as coreNewRoom
import game.vinto.room.nextAlarmAt as coreNextAlarmAt
import game.vinto.room.onAlarm as coreOnAlarm
import game.vinto.room.readyEnvelopes as coreReadyEnvelopes
import game.vinto.room.readyForNextRound as coreReadyForNextRound
import game.vinto.room.registrySize as coreRegistrySize
import game.vinto.room.removeBot as coreRemoveBot
import game.vinto.room.resolveRoomCode as coreResolveRoomCode
import game.vinto.room.roundRecording as coreRoundRecording
import game.vinto.room.seatCount as coreSeatCount
import game.vinto.room.seatForToken as coreSeatForToken
import game.vinto.room.sessionMs as coreSessionMs
import game.vinto.room.startGame as coreStartGame
import game.vinto.room.syncEnvelope as coreSyncEnvelope
import game.vinto.room.touchRoom as coreTouchRoom
import game.vinto.room.updatePresence as coreUpdatePresence
import game.vinto.room.viewForSeat as coreViewForSeat

/**
 * The worker's exported surface, delegating one-for-one to `shared/room`.
 *
 * The room and registry cores used to live in this module's jsMain, which made them
 * JavaScript-only: correct code that could not be unit-tested, only exercised through
 * wrangler gate scripts. They now live in `game.vinto.room` — a jvm+js module, tested on the
 * JVM — and this file is what remains here: the `@JsExport` names `index.mjs` imports,
 * unchanged, so the gate scripts and the deployed shell notice nothing.
 *
 * Nothing but delegation belongs in this file. A decision made here is a decision the JVM
 * tests cannot see, which is the exact situation the extraction ended.
 */

// --- the room -------------------------------------------------------------------------------

@JsExport
fun newRoom(roomId: String, seed: Double, difficulty: String, nowMs: Double): String =
    coreNewRoom(roomId, seed, difficulty, nowMs)

@JsExport
fun joinRoom(stateJson: String, token: String, nickname: String, nowMs: Double): String =
    coreJoinRoom(stateJson, token, nickname, nowMs)

@JsExport
fun addBot(stateJson: String, token: String, nowMs: Double): String =
    coreAddBot(stateJson, token, nowMs)

@JsExport
fun removeBot(stateJson: String, token: String, seatIndex: Int, nowMs: Double): String =
    coreRemoveBot(stateJson, token, seatIndex, nowMs)

@JsExport
fun startGame(stateJson: String, nowMs: Double): String = coreStartGame(stateJson, nowMs)

@JsExport
fun readyForNextRound(stateJson: String, token: String, nowMs: Double): String =
    coreReadyForNextRound(stateJson, token, nowMs)

@JsExport
fun updatePresence(stateJson: String, connectedSeatsCsv: String, nowMs: Double): String =
    coreUpdatePresence(stateJson, connectedSeatsCsv, nowMs)

@JsExport
fun onAlarm(stateJson: String, nowMs: Double): String = coreOnAlarm(stateJson, nowMs)

@JsExport
fun applyAction(stateJson: String, token: String, actionJson: String, nowMs: Double): String =
    coreApplyAction(stateJson, token, actionJson, nowMs)

@JsExport
fun viewForSeat(stateJson: String, seat: Int, nowMs: Double): String =
    coreViewForSeat(stateJson, seat, nowMs)

@JsExport
fun seatForToken(stateJson: String, token: String): Int = coreSeatForToken(stateJson, token)

@JsExport
fun eventsSince(stateJson: String, sinceIndex: Int): String =
    coreEventsSince(stateJson, sinceIndex)

@JsExport
fun lobbyView(stateJson: String, nowMs: Double): String = coreLobbyView(stateJson, nowMs)

// The envelope builders (choreography change 4.1): the same operations with the wire
// messages prebuilt per seat, so the JavaScript sends strings it does not read. The plain
// forms above stay exported for the gate harnesses.

@JsExport
fun applyActionEnvelopes(stateJson: String, token: String, actionJson: String, nowMs: Double): String =
    coreApplyActionEnvelopes(stateJson, token, actionJson, nowMs)

@JsExport
fun readyEnvelopes(stateJson: String, token: String, nowMs: Double): String =
    coreReadyEnvelopes(stateJson, token, nowMs)

@JsExport
fun alarmEnvelopes(stateJson: String, nowMs: Double): String =
    coreAlarmEnvelopes(stateJson, nowMs)

@JsExport
fun syncEnvelope(stateJson: String, seat: Int, sinceIndex: Int, nowMs: Double): String =
    coreSyncEnvelope(stateJson, seat, sinceIndex, nowMs)

@JsExport
fun roundRecording(stateJson: String, recordedAt: String): String =
    coreRoundRecording(stateJson, recordedAt)

@JsExport
fun seatCount(): Int = coreSeatCount()

@JsExport
fun nextAlarmAt(stateJson: String): Double = coreNextAlarmAt(stateJson)

@JsExport
fun sessionMs(): Double = coreSessionMs()

@JsExport
fun countdownMs(): Double = coreCountdownMs()

// --- the registry ---------------------------------------------------------------------------

@JsExport
fun newRegistry(): String = coreNewRegistry()

@JsExport
fun mintRoomCode(
    registryJson: String,
    randomBytes: String,
    isPublic: Boolean,
    hostNickname: String,
    sourceId: String,
): String = coreMintRoomCode(registryJson, randomBytes, isPublic, hostNickname, sourceId)

@JsExport
fun resolveRoomCode(registryJson: String, code: String): String =
    coreResolveRoomCode(registryJson, code)

@JsExport
fun listPublicRooms(registryJson: String, nowMs: Double): String =
    coreListPublicRooms(registryJson, nowMs)

@JsExport
fun maxLiveRooms(): Int = coreMaxLiveRooms()

@JsExport
fun maxRoomsPerSource(): Int = coreMaxRoomsPerSource()

@JsExport
fun forgetRoom(registryJson: String, code: String): String = coreForgetRoom(registryJson, code)

@JsExport
fun touchRoom(
    registryJson: String,
    code: String,
    humans: Int,
    seatsFilled: Int,
    startsAtEpochMs: Double,
): String = coreTouchRoom(registryJson, code, humans, seatsFilled, startsAtEpochMs)

@JsExport
fun registrySize(registryJson: String): Int = coreRegistrySize(registryJson)
