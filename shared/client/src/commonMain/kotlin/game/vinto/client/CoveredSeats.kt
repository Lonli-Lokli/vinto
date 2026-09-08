package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.protocol.botName

/**
 * A seat somebody has left, while a bot keeps it warm.
 *
 * The room hands a seat to a bot when its socket has been gone for the seat grace (design
 * R5), and it deliberately **never writes that into the game state**: `isHuman` and `isBot`
 * are inside the canonical hash, so a takeover written there would be a round whose recording
 * could not replay. What the room sends instead is `away` — the ids a bot is covering — on
 * every batch and every sync.
 *
 * Which left the table drawing a person: their name, their face, no machine mark, playing at
 * machine speed. Reported as exactly that, from a real game. So the reading of `away` lives
 * here, once, in the module that has no Compose and can therefore be tested without one.
 */

/**
 * What a seat is called *right now*.
 *
 * The person's nickname, or — while a bot is covering the seat — that seat's bot name, which
 * is the room's own [botName] rather than a second list invented here. The client draws its
 * portrait from the name (`portraitFor`), so the face follows without a second rule, and both
 * go back the moment their owner does: this is a projection, never a stored fact.
 */
fun seatName(view: PlayerView, playerId: String, away: Set<String>): String {
    val index = view.players.indexOfFirst { it.id == playerId }
    if (index < 0) return ""
    return if (playerId in away) botName(index) else view.players[index].nickname
}

/** Whether a machine is playing this seat: its own bot, or one covering somebody. */
fun isPlayedByAMachine(seat: game.vinto.engine.PlayerSeatView, away: Set<String>): Boolean =
    seat.isBot || seat.id in away

/**
 * What changed about who is being covered, as lines for the strip.
 *
 * A takeover is the largest thing that can happen to a table without a card moving, and the
 * log that narrates every draw and every toss-in said nothing about it. Both directions are
 * news: somebody's seat being taken, and somebody coming back to it.
 *
 * The **person** is named, not the bot — "Bob has gone" is the fact; that the seat now reads
 * as Ember is on the felt for anyone to see.
 */
fun awayChanges(was: Set<String>, now: Set<String>, view: PlayerView): List<Say> {
    if (was == now) return emptyList()
    fun who(id: String): Speaker = if (id == view.viewerId) {
        Speaker.You
    } else {
        view.players.firstOrNull { it.id == id }?.let { Speaker.Named(it.nickname) } ?: Speaker.Nobody
    }
    return (now - was).sorted().map { Say.SeatCovered(who(it)) } +
        (was - now).sorted().map { Say.SeatBack(who(it)) }
}
