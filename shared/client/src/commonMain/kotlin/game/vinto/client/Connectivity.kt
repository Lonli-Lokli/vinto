package game.vinto.client

/**
 * What the platform knows about the network, in the one vocabulary the screens read.
 *
 * Three answers rather than a boolean, and the third is the point. Aeroplane mode is a fact
 * every phone can state; a working connection is a guess every platform makes; and the
 * desktop, or a browser that only knows it is not *definitely* offline, cannot say either
 * way. A boolean would force that last case to pick a side, and whichever side it picked
 * would be wrong for somebody: "offline" on a working desktop is a menu that refuses to work,
 * and "online" is just the old behaviour with a new name.
 *
 * A type in this module and no platform code, like [RoomTrouble]: the phone's own APIs live
 * in the app, and what they answer is turned into this before a screen sees it.
 */
enum class Reachability {
    /** The platform has no dependable signal. Never shuts a door. */
    UNKNOWN,

    /** No network interface at all — aeroplane mode, or nothing connected. */
    OFFLINE,

    /** A network exists. Not a promise that anything answers on it. */
    ONLINE,
}

/** The one line the online menu says about the network, as a word the screen maps to a sentence. */
enum class OnlineWord {
    /** Nothing to say; the three doors work. */
    READY,

    /** There is no network, so none of them can, and the menu says so before a tap. */
    OFFLINE,
}

/**
 * The front door of online play: whether its three verbs can go anywhere, and what to say.
 *
 * The same split `lobbyUi` and `discoveryRows` make — the decision here, pure and tested,
 * and the composable draws what it is told. What happened without it: somebody opened the
 * public-room browser in aeroplane mode and was shown a failure for a tap the phone knew
 * could not work. The door is shut only on certainty; a platform that cannot tell leaves it
 * open, and the screens behind it keep their own failure handling for the network that is
 * there and dead.
 */
data class OnlineDoor(val open: Boolean, val word: OnlineWord)

fun onlineDoor(reachability: Reachability): OnlineDoor = when (reachability) {
    Reachability.OFFLINE -> OnlineDoor(open = false, word = OnlineWord.OFFLINE)
    Reachability.UNKNOWN, Reachability.ONLINE -> OnlineDoor(open = true, word = OnlineWord.READY)
}
