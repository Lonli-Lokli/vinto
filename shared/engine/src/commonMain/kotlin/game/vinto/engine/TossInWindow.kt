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

/**
 * Whether a card already thrown in is being played right now — one definition, for everybody
 * who asks.
 *
 * Not the same question as [tossInIsOpen], and the difference matters to two callers who both
 * used to answer it for themselves. `ActionValidator` asks it to decide **who may act**: while a
 * throw resolves, the actor is whoever owns the pending action rather than whoever's turn it is,
 * which is the whole point of the mechanic. `BotRunner` asks it to tell a queued throw from a
 * card stranded by a window that opened over it — the two arrive at `choosing-action` looking
 * identical, and the bot put every throw after the first one down unplayed for want of this line.
 *
 * The queue is the discriminator because it is the engine's own record of the fact: a queued
 * action stays at the head of `queuedActions` for exactly as long as it is the one being played
 * (`clearTossInAfterActionableCard` removes it on the way out).
 */
val GameState.resolvingATossIn: Boolean
    get() = activeTossIn?.queuedActions.orEmpty().isNotEmpty()

/**
 * Whether the seat on play has already put its card down — its turn is spent.
 *
 * A turn runs from the seat taking a card to the window opening on what they put down, and it
 * is only *over* at the far end of that. The plan reads this to decide whether a turn is still
 * somebody's to write (`CoalitionPlan.lockingLaneOf`): a card in the hand keeps the turn open,
 * because saying what to do with a face-up drawn card is the whole point of the plan, and a card
 * on the pile closes it, because there is nothing left to decide.
 *
 * Read from the sub-phase, which is the engine's own statement about where a turn is: `idle` and
 * `ai_thinking` are a seat about to take a card, `choosing` is one holding it, and everything
 * else is after.
 *
 * The one exclusion is the moment a **thrown** card's action is being played, which moves
 * `currentPlayerIndex` to whoever threw it — so the sub-phase is then about a seat that has not
 * taken its turn at all. That is a queued action *in flight*, which is a pending one alongside a
 * queue, and not merely a queue: throws sit in it from the moment they are made, which is well
 * before the window closes and was long enough to make this answer always false.
 */
val GameState.turnIsSpent: Boolean
    get() = !(resolvingATossIn && pendingAction != null) && when (subPhase) {
        GameSubPhase.IDLE, GameSubPhase.AI_THINKING, GameSubPhase.CHOOSING -> false
        else -> true
    }

/** The same question, of the only shape a screen ever gets. See [turnIsSpent]. */
val PlayerView.turnIsSpent: Boolean
    get() = !(activeTossIn?.queuedActions.orEmpty().isNotEmpty() && pendingAction != null) &&
        when (subPhase) {
            GameSubPhase.IDLE, GameSubPhase.AI_THINKING, GameSubPhase.CHOOSING -> false
            else -> true
        }
