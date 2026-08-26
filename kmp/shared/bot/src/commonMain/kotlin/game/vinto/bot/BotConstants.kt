package game.vinto.bot

/**
 * Weights for the swap decision, ported from `packages/bot/src/lib/constants.ts`.
 *
 * The ordering is the strategy in miniature: knowing your hand beats holding fewer cards,
 * which beats a marginally better score. A bot that optimises raw score first ends up
 * blind, and a blind bot cannot call Vinto at all — [shouldCallVintoByScore] requires full
 * self-knowledge.
 */
object SwapWeights {
    /** Highest priority: maximise knowledge. */
    const val KNOWLEDGE = 100.0

    /** Second priority: minimise hand size. */
    const val HAND_SIZE = 50.0

    /** Score matters least, but not nothing — 15 rather than 1 to stop catastrophic swaps. */
    const val SCORE = 15.0
}

/**
 * How hard to protect the two cards worth protecting, and how sharply to punish giving one
 * away.
 *
 * The amplifiers look absurd until you multiply them out: swapping a Joker for a 6 costs
 * `7 × 15 × 3.0 × 100 = 31,500`. They are not tuned values, they are a floor — the point is
 * that no combination of other terms can outvote them, so the bot *never* does it.
 */
object CardProtection {
    /** Joker is the best card in the game at -1. */
    const val JOKER_MULTIPLIER = 3.0

    /** King is 0 points and a powerful action. */
    const val KING_MULTIPLIER = 2.5

    const val JOKER_PENALTY_AMPLIFIER = 100.0
    const val KING_PENALTY_AMPLIFIER = 100.0

    /** Applied whenever a better card would be swapped out for a worse one. */
    const val GENERAL_SWAP_PENALTY_MULTIPLIER = 10.0
}

/**
 * A memory below this confidence is a hunch, not a fact, and is estimated rather than
 * trusted. One threshold for the whole bot: the same 0.5 decides what the score estimator
 * counts, what determinization deals, what the solver believes, and what a declaration
 * claims — a memory the bot acts on anywhere is a memory it acts on everywhere.
 */
internal const val TRUSTED_CONFIDENCE = 0.5

/**
 * How [VintoRoundSolver] steers the live call around [shouldCallVintoByScore]'s plain
 * `hand ≤ 0` rule. Both directions are gated on the solver's confidence — the fraction of
 * the table the bot has actually seen — because worst-case analysis over cards nobody has
 * looked at is not knowledge:
 *
 *  - a believed-zero hand calls *unless* the solver vetoes with real knowledge. A blind
 *    solver cannot veto, or the endgame would become unreachable exactly when the bots
 *    play worst.
 *  - a believed hand up to [ENABLER_MAX_SCORE] calls only when the solver approves at even
 *    higher confidence. This is the only path by which a positive hand ever calls, and it
 *    is what ends the games where nobody ever assembles a zero.
 */
object VintoCallWiring {
    /** The solver may only overrule a zero hand when it has really seen the table. */
    const val VETO_CONFIDENCE = 0.55

    /** A positive hand needs better evidence to call than a zero hand needs to be stopped. */
    const val ENABLER_CONFIDENCE = 0.6

    /** Above this believed score the solver is not consulted; the hand is simply not good. */
    const val ENABLER_MAX_SCORE = 4

    /**
     * After this many table laps without a call, provable safety stops being the bar.
     *
     * A table of small hands can reach a stalemate: everyone believes their whole hand,
     * everyone is at eight to fourteen points, nobody can improve, and the worst-case
     * analysis refuses every call forever — the deck just reshuffles and the game never
     * ends. Real players end such games by judging they are *relatively* ahead and
     * calling. Past this point the bot does the same: it calls when its believed score is
     * no worse than the best it *expects* of any opponent — a bar the player with the
     * lowest believed hand always clears, which is what guarantees games end.
     */
    const val LATE_GAME_LAPS = 12
}
