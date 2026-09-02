package game.vinto.room

import game.vinto.protocol.PlayerProfile
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The doors a lobby keeps shut, and the one rule about a countdown that is easy to get wrong.
 *
 * Every refusal here is a sentence a client is shown, and until now none of them had been
 * seen by a test: the happy path through the lobby was covered and every `return` beside it
 * was not. A refusal that is wrong is worse than a crash — it tells a person something false
 * about a room they can see — so each is pinned by its wording as well as its effect.
 */
class LobbyRefusalsTest {

    // ------------------------------------------------------------------ joining

    @Test
    fun nobodyJoinsAGameAlreadyDealt() {
        val late = decodeJoin(joinRoom(dealtRoom(), "token-carol", "Carol", LATER))

        assertEquals("the game has already started", late.error)
        assertEquals(-1, late.seat)
        assertEquals(2, late.state.humanCount, "the refusal seated somebody anyway")
    }

    @Test
    fun aFullTableIsFull() {
        var state = lobbyOfTwo()
        state = encode(decodeJoin(joinRoom(state, "token-carol", "Carol", START)).state)
        state = encode(decodeJoin(joinRoom(state, "token-dave", "Dave", START)).state)

        val fifth = decodeJoin(joinRoom(state, "token-eve", "Eve", START))

        assertEquals("room is full", fifth.error)
        assertEquals(-1, fifth.seat)
    }

    /**
     * A newcomer may take a filler bot's seat, and never a seat a bot is holding for somebody.
     *
     * The distinction is `isFiller`: a bot somebody added to fill the table is displaceable; a
     * bot playing a disconnected human's seat is not, because that seat belongs to its token
     * and a stranger taking it while its owner reconnects would make the token guarantee
     * meaningless in the one situation it exists for.
     */
    @Test
    fun aNewcomerDisplacesAFillerBotButNeverASeatABotIsHoldingForSomebody() {
        val lobby = decodeRoom(lobbyOfTwo())
        val held = lobby.copy(
            seats = lobby.seats.map {
                when (it.index) {
                    // Bob's seat, played by a bot while he is away: bot *and* token.
                    1 -> it.copy(isBot = true, botPlayedWhileAway = true)
                    // Two fillers.
                    2, 3 -> it.copy(isBot = true, profile = PlayerProfile(botName(it.index)))
                    else -> it
                }
            },
        )

        val carol = decodeJoin(joinRoom(encode(held), "token-carol", "Carol", START))
        assertNull(carol.error)
        assertEquals(2, carol.seat, "Carol was seated somewhere other than the first filler's seat")
        val taken = carol.state.seats[2]
        assertEquals(Sha256.hex("token-carol"), taken.tokenHash)
        assertFalse(taken.isBot, "the seat is a person's now")
        assertEquals("Carol", taken.profile?.nickname)

        val bob = carol.state.seats[1]
        assertTrue(bob.isBot && bob.tokenHash != null, "Bob's held seat was touched")

        val dave = decodeJoin(joinRoom(encode(carol.state), "token-dave", "Dave", START))
        assertEquals(3, dave.seat, "the second filler goes next")

        val eve = decodeJoin(joinRoom(encode(dave.state), "token-eve", "Eve", START))
        assertEquals("room is full", eve.error, "Bob's seat was given to a stranger")
    }

    // ------------------------------------------------------------------ the countdown

    /**
     * Design R2a's two rules, which have to hold at once: a human taking a bot's seat while
     * the countdown runs does not push the start back, while emptying a seat and refilling it
     * gives everybody the full ten seconds again — because emptying passed through `LOBBY`.
     */
    @Test
    fun takingAFillersSeatMidCountdownDoesNotRestartIt() {
        var state = lobbyOfTwo()
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)
        val counting = decodeJoin(addBot(state, TOKEN_A, START)).state
        assertEquals(RoomPhase.STARTING, counting.phase)
        val deadline = assertNotNull(counting.startsAtEpochMs)

        val carol = decodeJoin(joinRoom(encode(counting), "token-carol", "Carol", START + 8_000))
        assertNull(carol.error)
        assertEquals(RoomPhase.STARTING, carol.state.phase, "a full table stopped counting down")
        assertEquals(deadline, carol.state.startsAtEpochMs, "the start moved for somebody sitting down")
        assertEquals(3, carol.state.humanCount)
    }

    @Test
    fun takingABotOutCancelsTheCountdownAndPuttingOneBackRestartsItInFull() {
        var state = lobbyOfTwo()
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)
        val counting = decodeJoin(addBot(state, TOKEN_A, START)).state
        val bot = counting.seats.first { it.isFiller }

        val cancelled = decodeJoin(removeBot(encode(counting), TOKEN_B, bot.index, START + 8_000))
        assertNull(cancelled.error, "any seated player may take a bot back out: ${cancelled.error}")
        assertEquals(RoomPhase.LOBBY, cancelled.state.phase)
        assertNull(cancelled.state.startsAtEpochMs, "a cancelled countdown kept its deadline")
        assertFalse(cancelled.state.seats[bot.index].occupied, "the seat is empty again")

        val restarted = decodeJoin(addBot(encode(cancelled.state), TOKEN_B, START + 8_000)).state
        assertEquals(RoomPhase.STARTING, restarted.phase)
        assertEquals(START + 8_000 + countdownMs(), restarted.startsAtEpochMs, "the new countdown is a full one")
    }

    // ------------------------------------------------------------------ bots

    @Test
    fun onlyASeatedPlayerMayAddOrRemoveABot() {
        val lobby = lobbyOfTwo()

        val added = decodeJoin(addBot(lobby, STRANGER, START))
        assertEquals("only a seated player may add a bot", added.error)
        assertEquals(2, added.state.seats.count { it.occupied }, "the stranger's bot took a seat")

        val withBot = encode(decodeJoin(addBot(lobby, TOKEN_A, START)).state)
        val removed = decodeJoin(removeBot(withBot, STRANGER, 2, START))
        assertEquals("only a seated player may remove a bot", removed.error)
        assertTrue(removed.state.seats[2].isBot, "the stranger's removal took")
    }

    @Test
    fun botsAreNeitherAddedNorRemovedOnceTheGameIsDealt() {
        val dealt = dealtRoom()

        assertEquals("the game has already started", decodeJoin(addBot(dealt, TOKEN_A, LATER)).error)
        assertEquals("the game has already started", decodeJoin(removeBot(dealt, TOKEN_A, 2, LATER)).error)
    }

    @Test
    fun aBotNeedsAnEmptySeat() {
        var state = lobbyOfTwo()
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)

        val fifth = decodeJoin(addBot(state, TOKEN_A, START))

        assertEquals("every seat is taken", fifth.error)
    }

    @Test
    fun onlyAFillerBotCanBeTakenOut() {
        val withBot = encode(decodeJoin(addBot(lobbyOfTwo(), TOKEN_A, START)).state)

        fun removing(seat: Int): String? = decodeJoin(removeBot(withBot, TOKEN_A, seat, START)).error

        assertEquals("unknown seat 9", removing(9))
        assertEquals("seat 1 is not a bot", removing(1), "a person was removable")
        assertEquals("seat 3 is not a bot", removing(3), "an empty seat was removable")
        assertNull(removing(2), "the filler itself is not")
    }

    // ------------------------------------------------------------------ dealing

    @Test
    fun dealingIsRefusedOutsideAnExpiredCountdown() {
        assertEquals("the room is not starting", decodeJoin(startGame(lobbyOfTwo(), LATER)).error)

        // A countdown that lost a human before it expired: the phase alone is not enough to
        // deal on, and the deal must not trust it.
        val lobby = decodeRoom(lobbyOfTwo())
        val counting = lobby.copy(
            phase = RoomPhase.STARTING,
            startsAtEpochMs = START,
            seats = lobby.seats.map { if (it.index == 1) Seat(index = 1) else it },
        )
        val short = decodeJoin(startGame(encode(counting), LATER))
        assertEquals("a game needs 2 humans and four seats", short.error)
        assertEquals(RoomPhase.STARTING, short.state.phase, "a refused deal changed the room")
    }

    // ------------------------------------------------------------------ names

    /**
     * A nickname is displayed to strangers who never agreed to read whatever somebody sent.
     * One rule, applied on the way in: whitespace collapsed, markup and control characters
     * dropped, sixteen characters at most, and a seat that ends up nameless is named after
     * its index rather than left blank.
     */
    @Test
    fun aNicknameIsTrimmedToSomethingDisplayable() {
        assertEquals("Mary Ann", sanitiseNickname("  Mary " + "\t" + "\n" + "  Ann  ", 0))
        assertEquals("Ann script", sanitiseNickname("Ann <script>", 0))
        assertEquals("O'Brien-Smith_1.", sanitiseNickname("O'Brien-Smith_1.", 0), "a little punctuation is allowed")
        assertEquals(16, sanitiseNickname("A".repeat(40), 0).length)
        assertEquals("Player 3", sanitiseNickname("   ", 2))
        assertEquals("Player 1", sanitiseNickname("!!!", 0), "nothing displayable is nothing")
        assertEquals("", cleanNickname("<>"), "the registry's version has no fallback: a host may be nameless")

        val seated = decodeJoin(joinRoom(newRoom("room-N", 1.0, "easy", START), TOKEN_A, "  <b>Ann</b>  ", START))
        assertEquals("bAnnb", seated.state.seats[0].profile?.nickname, "the rule is applied where a name comes in")
    }

    @Test
    fun aBotIsNamedByItsSeatSoTwoNeverCollide() {
        assertEquals(listOf("Leo", "Raph", "Mikey", "Don"), (0..3).map(::botName))
        assertEquals("Bot 8", botName(7), "a seat that does not exist still gets a name rather than a crash")

        var state = lobbyOfTwo()
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)
        state = encode(decodeJoin(addBot(state, TOKEN_A, START)).state)
        assertEquals(listOf("Mikey", "Don"), decodeRoom(state).seats.drop(2).map { it.profile?.nickname })
    }
}
