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
