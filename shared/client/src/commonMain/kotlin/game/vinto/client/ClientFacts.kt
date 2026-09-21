package game.vinto.client

import game.vinto.protocol.ClientPlatform

/**
 * What a client is, for the one count that asks: how are online players playing?
 *
 * Three facts, all optional, none of them anything a player typed. They ride on the join and
 * are counted once, when a human takes a seat — see `AnalyticsEvent.ClientJoined` for why this
 * is online only and why a solo round still says nothing about the machine it was played on.
 *
 * All-null by default, which is what a test, an older build and anything that does not care
 * send. The room drops a platform or a language it does not recognise rather than storing it,
 * so nothing here can widen what reaches the store.
 */
data class ClientFacts(
    val platform: ClientPlatform? = null,
    /** A tag naming a `Locale`; anything else is dropped at the room. */
    val locale: String? = null,
    /** The commit count this build was made from — the number the stores index. */
    val build: Int? = null,
)
