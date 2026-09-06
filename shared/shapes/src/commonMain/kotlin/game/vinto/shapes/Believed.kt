package game.vinto.shapes

/**
 * What the table currently believes about one card, once everything said about it is put
 * together.
 *
 * [candidates] is never empty: a card nobody has spoken about is every rank, which is the same
 * as knowing nothing and is what the rest of the code should read rather than special-casing
 * absence.
 */
data class Believed(
    val candidates: Set<Rank>,
    /** Two seats said things that cannot both be true. Neither is dropped and neither wins. */
    val disputed: Boolean,
    /** Who said what, in the order it was said, for a table that has to show its sources. */
    val sources: List<Claim>,
) {
    /** Exactly one rank survives, so a King may be declared from it and a toss-in matched. */
    val rankKnown: Boolean get() = candidates.size == 1

    /**
     * Every candidate is worth the same, so the coalition can plan around the card's value
     * even where its rank is still open — a King and a Joker differ, but a Jack and a Queen
     * are both ten.
     */
    val valueKnown: Boolean get() = candidates.map(::getCardValue).distinct().size == 1

    /** The value to plan with: exact where [valueKnown], and the mean of what is left where not. */
    val value: Int
        get() = candidates.sumOf(::getCardValue).let { total ->
            // Integer division deliberately: a plan that works in halves is a plan whose
            // numbers nobody can check against a hand of cards.
            total / candidates.size
        }
}

/**
 * Everything said about [position] in [owner]'s hand, combined.
 *
 * The combining rule is the whole design, and the case worth optimising for is agreement
 * rather than disagreement:
 *
 *  - **consistent claims intersect.** "It is an action card" and "it is a King or a Queen"
 *    make `{King, Queen}`; "it is low" and "it is a 2 or a 7" make `{2}`. Two people pooling
 *    partial memories end up knowing more than either did, which is what the coalition's
 *    channel is *for*;
 *  - **inconsistent claims are flagged and kept.** Where the intersection empties, the card is
 *    [Believed.disputed] and its candidates are the union. Nothing is dropped and nothing is
 *    adjudicated — a card two people remember differently is information, and usually one of
 *    them can work out whose memory is stale.
 *
 * A seat's own later claim **replaces** its earlier one before any of this: a person changing
 * their mind is not a disagreement with themselves.
 */
fun believedAt(owner: PlayerState, position: Int): Believed {
    val about = standingClaims(owner).filter { position in it.positions }
    if (about.isEmpty()) return Believed(ALL_RANKS.toSet(), disputed = false, sources = emptyList())

    val sets = about.map { it.ranks.toSet() }
    val agreed = sets.reduce { left, right -> left intersect right }

    return if (agreed.isEmpty()) {
        Believed(sets.reduce { left, right -> left union right }, disputed = true, sources = about)
    } else {
        Believed(agreed, disputed = false, sources = about)
    }
}

/**
 * The claims that still stand, one per speaker per subject: a seat's later claim about the
 * same positions supersedes its earlier one.
 *
 * Kept in the order first spoken so a table showing its sources does not reshuffle itself
 * every time somebody corrects themselves.
 */
fun standingClaims(owner: PlayerState): List<Claim> {
    val claims = owner.claims ?: return emptyList()
    val standing = mutableListOf<Claim>()
    for (claim in claims) {
        // Saying a card could be any rank is taking back what was said about it: the earlier
        // word goes, and nothing is believed in its place.
        if (claim.vacuous) {
            standing.removeAll { earlier ->
                earlier.by == claim.by && earlier.positions.any { it in claim.positions }
            }
            continue
        }
        // A later claim supersedes any earlier one **from the same speaker that overlaps it**,
        // not merely one about the identical positions. Exact-list equality was the first
        // rule and it is wrong in a way a player reaches easily: say "these two are a King and
        // an Ace, I forget which", then peek one and say what it is, and the speaker's two
        // claims intersect to nothing and the card is marked *disputed* — a seat disagreeing
        // with itself, which is precisely what D3b says cannot happen. The only escape was to
        // withdraw everything they had said about that hand.
        standing.removeAll { earlier ->
            earlier.by == claim.by && earlier.positions.any { it in claim.positions }
        }
        standing += claim
    }
    return standing
}
