package game.vinto.engine

import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase

/**
 * Whether a toss-in window is open for throws — one definition, for everybody who asks.
 *
 * There were two, and they disagreed. The obvious one is `ActiveTossIn.waitingForInput`, and it
 * is **not reliable**: when the last queued toss-in action finishes, the engine puts the
 * sub-phase back to `TOSS_QUEUE_ACTIVE` so the window is open again — and leaves that flag
 * false. The field cannot simply be corrected, because it is inside the canonical hash and the
 * corpus records what both engines did (`TossInUtils`, and `CorpusReplayTest` diverging at
 * action 54 of `selfplay-moderate-1` when it was).
 *
 * What that cost, before this existed: the bots read the sub-phase and knew the window was open,
 * the rail read the sub-phase and offered the throw — and the room read the flag, found nobody
 * to be waiting on, and set **no deadline**. An open window, with a turn behind it, and nothing
 * on any clock to end it. The felt's own corner and the countdown read the flag too, so the
 * table showed no window while the rail offered one.
 *
 * So the question is answered from the sub-phase, which is the engine's actual statement about
 * where the turn is, and the flag is left to the recordings that pin it.
 */
val GameState.tossInIsOpen: Boolean
    get() = activeTossIn != null && subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE

/** The same question, of the only shape a screen ever gets. See [tossInIsOpen]. */
val PlayerView.tossInIsOpen: Boolean
    get() = activeTossIn != null && subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE
