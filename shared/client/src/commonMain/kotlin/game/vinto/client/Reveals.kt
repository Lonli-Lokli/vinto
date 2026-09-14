package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal

/**
 * The reveals that still stand after a move.
 *
 * A card turned face up for the table — a throw that missed, the card a King pointed at — is
 * public for as long as it lies where it was shown, and a [PublicReveal] is a *position*. When
 * the card leaves, the reveal has to go with it: left where it was, it described whatever card
 * took the place next, and a plan naming that card read as resting on a claim the reveal had
 * "proved wrong" — an error on the felt that nobody could account for (product owner).
 *
 * A client never sees a hidden card's identity, so the card is followed the way the felt
 * follows it: by the flights the move drew. A card that flies out of a seat takes its reveal
 * with it — to the seat it lands in for a trade, away for anything else — and a hand a card has
 * left closes up, so the reveals above the gap slide down a place with the cards they are
 * about. `LocalGameSession` and `RemoteGameSession` both keep their reveals this way, and
 * `RevealsFollowTheCardTest` holds the local one against the engine's own answer, whole games
 * at a time.
 */
internal fun List<PublicReveal>.following(
    scenes: List<Scene>,
    before: PlayerView,
    after: PlayerView,
): List<PublicReveal> {
    val moves = scenes.flatten().filterIsInstance<Beat.Move>()
    return mapNotNull { reveal -> reveal.followed(moves, before, after) }
        .filter { it.position in after.hand(it.playerId).indices }
}

private fun PublicReveal.followed(
    moves: List<Beat.Move>,
    before: PlayerView,
    after: PlayerView,
): PublicReveal? {
    val here = Anchor.Seat(playerId, position)
    val flight = moves.firstOrNull { it.from == here }
    if (flight != null) {
        // Traded: the reveal lands with the card. Thrown, put down or swapped out: it is gone.
        val landed = flight.to as? Anchor.Seat ?: return null
        return copy(playerId = landed.playerId, position = landed.position)
    }

    // A hand closes up behind every card that left it from below this one — and only a hand
    // that has lost a card has closed up: a swap-out sends one card out and lands another in
    // the same place, and shifts nothing.
    val closedUp = after.hand(playerId).size < before.hand(playerId).size
    if (!closedUp) return this
    val below = moves.count { move ->
        val from = move.from as? Anchor.Seat
        from != null && from.playerId == playerId && from.position < position && move.to !is Anchor.Seat
    }
    return copy(position = position - below)
}

private fun PlayerView.hand(playerId: String) = players.firstOrNull { it.id == playerId }?.cards.orEmpty()
