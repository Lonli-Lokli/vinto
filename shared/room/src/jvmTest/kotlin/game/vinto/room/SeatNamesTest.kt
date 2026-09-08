package game.vinto.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The names on the table are the room's, not the deal's.
 *
 * `initializeGame` deals a *solo* cast — seat zero called "You", the other three the elements —
 * because that is what it is for, and the room then reuses it for an online round. Everything the
 * client draws a seat with keys off the name it finds in the game state: the plate shows
 * `PlayerSeatView.nickname`, `LocalFaces` looks a chosen face up by that same string, and
 * `portraitFor` falls back to the element emblem it names. So a room that deals without renaming
 * seats hands four people a table where nobody is called what they are called, one seat is
 * labelled "You" and is somebody else, and every chosen face silently misses its key.
 *
 * Reported from a browser: *"I do not see other people names/avatars, also I am located on side
 * while I must be always on the bottom"* — which is one bug wearing two faces. The viewer WAS in
 * the near chair; the seat reading "You" was another player, three chairs away, and reading it as
 * yourself is the only sane thing to do with it.
 *
 * `BOT_NAMES` is written to match `portraitFor` exactly, and the comment above it says so, which
 * is the clearest evidence these names were always meant to travel.
 */
class SeatNamesTest {

    @Test
    fun everySeatWearsTheNameTheRoomGaveIt() {
        val room = decodeRoom(dealtRoom())
        val game = checkNotNull(room.game) { "a dealt room has a game" }

        room.seats.forEachIndexed { index, seat ->
            val expected = checkNotNull(seat.profile?.nickname) { "seat $index has no profile" }
            assertEquals(
                expected,
                game.players[index].nickname,
                "seat $index is drawn as '${game.players[index].nickname}' and is $expected",
            )
        }
    }

    /**
     * And the deal's own cast is gone, which is the half a reader can check at a glance.
     *
     * "You" is the tell: it is `initializeGame`'s name for the offline human seat, it means
     * nothing at a table of four, and at most one person can be it.
     */
    @Test
    fun nobodyAtAnOnlineTableIsCalledYou() {
        val game = checkNotNull(decodeRoom(dealtRoom()).game)
        assertTrue(
            game.players.none { it.nickname == "You" || it.name == "You" },
            "the deal's solo cast reached an online table: ${game.players.map { it.nickname }}",
        )
    }

    /**
     * The people keep the room's own name for them; the filler seats keep one `portraitFor` draws.
     *
     * Not compared against what the joiner asked to be called: a nickname is *minted* rather than
     * typed, so "Ann" reaches the room and "Amber Otter" comes back out of it, which is the whole
     * of `NicknameTest`'s subject. What matters here is that the two names agree.
     */
    @Test
    fun thePeopleAreNamedAndTheBotsAreElements() {
        val room = decodeRoom(dealtRoom())
        val game = checkNotNull(room.game)

        game.players.filterIndexed { index, _ -> room.seats[index].tokenHash != null }.forEach {
            assertTrue(it.isHuman, "a seat with a token is not drawn as a person")
            assertTrue(
                it.nickname !in listOf("You", "Gale", "Ember", "Tide", "Dune"),
                "a person is wearing the deal's own name: '${it.nickname}'",
            )
        }
        game.players.filter { it.isBot }.forEach {
            assertTrue(
                it.nickname in listOf("Gale", "Ember", "Tide", "Dune"),
                "'${it.nickname}' is not a name `portraitFor` can draw an emblem for",
            )
        }
    }
}
