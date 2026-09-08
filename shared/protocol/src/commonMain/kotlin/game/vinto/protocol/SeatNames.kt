package game.vinto.protocol

/**
 * What a bot is called at a networked table, by the seat it fills.
 *
 * They were "Bot 2" and "Bot 3", which is a slot number rather than an opponent, and it sat
 * next to real people's names. The four are the same three the offline game deals, plus
 * **Gale**, who never appears there because offline seat zero is the human. Online it can be a
 * bot, so it is in the list for exactly that case.
 *
 * Each name is the emblem on its seat's portrait — a leaf, a flame, a crescent, a ridge
 * (`brand/avatars/`). That is not decoration: the portraits have to be told apart without
 * colour (`vydanne.config.mjs` claims as much to Apple), and a name that says which shape it
 * is makes the picture and the label agree instead of competing.
 *
 * By seat rather than in order taken, so the same seat is the same opponent every time and two
 * bots can never collide on a name. `portraitFor` in the client matches on these exactly.
 *
 * **In `protocol` rather than in the room**, which is where it started, because the client
 * needs it too: a seat a bot has taken over is drawn as that bot, and a client that invented
 * its own name for it would put a name on the felt the room had never heard of. The same
 * reasoning moved `looksLikeRoomCode` here — one declaration, so the two ends cannot disagree.
 */
val BOT_NAMES = listOf("Gale", "Ember", "Tide", "Dune")

fun botName(seatIndex: Int): String =
    BOT_NAMES.getOrElse(seatIndex) { "Bot ${seatIndex + 1}" }
