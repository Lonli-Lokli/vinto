package game.vinto.engine.cases

import game.vinto.engine.MutableGameState
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase

/**
 * CALL_VINTO — the player declares they hold the lowest hand, starting the final round.
 *
 * Everyone else becomes a coalition against the caller, and the caller can no longer take
 * part in toss-ins — so any toss-in in progress is cleared. Whether the turn also advances
 * depends on when Vinto was called: during a toss-in the caller has already completed their
 * turn, so play moves on; before that, they still finish it.
 *
 * Ported from `legacy-web/packages/engine/src/lib/cases/call-vinto.ts`.
 */
fun handleCallVinto(state: MutableGameState, action: GameAction.CallVinto): Boolean {
    val playerId = action.payload.playerId

    state.vintoCallerId = playerId
    state.finalTurnTriggered = true
    state.phase = GamePhase.FINAL

    val opponentIds = state.players.filter { it.id != playerId }.map { it.id }
    for (player in state.players) {
        player.isVintoCaller = player.id == playerId
        if (!player.isVintoCaller) {
            player.coalitionWith.clear()
            player.coalitionWith.addAll(opponentIds)
        }
    }

    val wasDuringTossIn = state.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE ||
        state.subPhase == GameSubPhase.TOSS_QUEUE_PROCESSING

    val tossIn = state.activeTossIn ?: return true
    val originalPlayerIndex = tossIn.originalPlayerIndex
    state.activeTossIn = null

    if (!wasDuringTossIn) {
        state.subPhase = GameSubPhase.IDLE
        return true
    }

    // This advance intentionally differs from `advanceTurnAfterTossIn`: `turnNumber` moves
    // only on wrap to seat 0, `roundNumber` never moves, and there is NO low-deck refill.
    // That is what the TypeScript engine does — `selfplay-moderate-18` calls Vinto with one
    // card on the deck and its recorded hash proves TS did not reshuffle — and forty-two
    // parity recordings run through this path, so it stays byte-for-byte. None of it is a
    // rules matter: the counters feed no legality check, and a final round that starts on a
    // dry deck still ends lawfully through the FINAL-phase `END_ROUND` escape.
    state.currentPlayerIndex = (originalPlayerIndex + 1) % state.players.size
    if (state.currentPlayerIndex == 0) state.turnNumber++

    state.subPhase =
        if (state.players[state.currentPlayerIndex].isBot) GameSubPhase.AI_THINKING
        else GameSubPhase.IDLE

    return true
}
