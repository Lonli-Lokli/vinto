package game.vinto.engine

import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType

/**
 * What kind of target selection a rank's action requires.
 *
 * Ported from `packages/engine/src/lib/utils/action-utils.ts`. Ranks with no action return
 * null, which is how the caller tells "no target needed" from "target still to choose".
 */
fun getTargetTypeFromRank(rank: Rank): TargetType? = when (rank) {
    Rank.SEVEN, Rank.EIGHT -> TargetType.OWN_CARD
    Rank.NINE, Rank.TEN -> TargetType.OPPONENT_CARD
    Rank.JACK -> TargetType.SWAP_CARDS
    Rank.QUEEN -> TargetType.PEEK_THEN_SWAP
    Rank.KING -> TargetType.DECLARE_ACTION
    Rank.ACE -> TargetType.FORCE_DRAW
    Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.JOKER -> null
}
