package game.vinto.room

import game.vinto.protocol.LobbyView
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two people waiting for a third who is not coming.
 *
 * `MIN_HUMANS` is two, so two humans are already enough to play. `canStart` also wants every
 * seat filled, and filling the last two means somebody realising they may add a bot and doing
 * it — which nobody does. So what actually happened is that two people sat in a lobby waiting
 * for a fourth, and ten minutes later the lobby sweep deleted the room out from under them.
 * Two humans and two bots is a real game and they were never offered it, while a Durable
 * Object held storage for the whole wait.
 *
 * The room offers now, once, at five minutes — and offers it the way `addBot` already does, by
 * filling the seats and running the ordinary countdown, so declining is taking a bot back out.
 * Nothing new had to be invented for the decline; that is the point of doing it this way.
 */
class LobbyNudgeTest {

    @Test
    fun twoPeopleWaitingAreOfferedAGameRatherThanADeletedRoom() {
        val waiting = lobbyOfTwo()

        // Nothing has happened yet at four minutes: the offer is not a hair trigger, and a
        // third player still has time to walk in.
        val early = alarm(waiting, NOW + 4 * MINUTE)
        assertEquals(RoomPhase.LOBBY, early.state.phase)
        assertNull(early.state.startsAtEpochMs)
        assertFalse(early.deleted)

        val offered = alarm(waiting, NOW + 5 * MINUTE).state
        assertEquals(RoomPhase.STARTING, offered.phase, "no offer was made")
        assertNotNull(offered.startsAtEpochMs, "the seats filled but nothing was counting down")
        assertTrue(offered.seats.all { it.occupied }, "the empty seats were not filled")
        assertEquals(2, offered.seats.count { it.isBot })
        assertEquals(2, offered.humanCount, "a human's seat was taken by a bot")
    }

    /**
     * The offer stands longer than a countdown somebody started themselves.
     *
     * Ten seconds is right for a person who has just tapped "add a bot" and is looking at the
     * screen. This one arrives unprompted at somebody five minutes into a wait, who may well
     * have put the phone down — so it has to survive being picked back up.
     */
    @Test
    fun anUnpromptedOfferOutlastsOneSomebodyAskedFor() {
        val offered = alarm(lobbyOfTwo(), NOW + 5 * MINUTE).state
        val stands = offered.startsAtEpochMs!! - (NOW + 5 * MINUTE)
        assertTrue(stands > countdownMs(), "the offer stands for only ${stands}ms")
    }

    /** And it does deal, when nobody stops it. */
    @Test
    fun anOfferNobodyDeclinesBecomesAGame() {
        val offered = alarm(lobbyOfTwo(), NOW + 5 * MINUTE).state
        val dealt = alarm(encode(offered), offered.startsAtEpochMs!! + 1)

        assertTrue(dealt.started, "the offer expired without dealing")
        assertEquals(RoomPhase.PLAYING, dealt.state.phase)
        assertNotNull(dealt.state.game)
    }

    /**
     * Declining is taking a bot back out, and the room does not ask twice.
     *
     * The failure this rules out is the one that makes an offer not an offer: without a record
     * that it was made, the very next alarm re-fills the seats over the refusal, and a player
     * who has said no watches the countdown restart every time the room wakes up.
     */
    @Test
    fun aDeclinedOfferIsNotMadeAgain() {
        val offered = alarm(lobbyOfTwo(), NOW + 5 * MINUTE).state
        val bot = offered.seats.first { it.isBot }

        val declined = decodeJoin(removeBot(encode(offered), TOKEN_A, bot.index, NOW + 5 * MINUTE))
        assertNull(declined.error, "a seated player could not decline: ${declined.error}")
        assertEquals(RoomPhase.LOBBY, declined.state.phase, "the countdown survived the decline")

        val after = alarm(encode(declined.state), NOW + 6 * MINUTE)
        assertEquals(RoomPhase.LOBBY, after.state.phase, "the room made the same offer twice")
        assertNull(after.startsAtEpochMs())
    }

    /**
     * After declining, a bot the player adds themselves is *theirs*.
     *
     * The first version of this carried one flag for two facts — "the offer has been made",
     * which is set once and never cleared, and "the countdown on screen is the room's", which
     * has to be cleared the moment the countdown is. They go out of step in exactly this
     * sequence: decline, then give up and add a bot by hand, and the screen tells the player
     * nobody came and the room filled the seats — crediting the room for what they just did.
     */
    @Test
    fun aBotAddedByHandAfterDecliningIsNotTheRoomsDoing() {
        val offered = alarm(lobbyOfTwo(), NOW + 5 * MINUTE).state
        assertTrue(view(offered).botsOffered, "the room's own countdown did not say so")

        val bot = offered.seats.first { it.isBot }
        val declined = decodeJoin(removeBot(encode(offered), TOKEN_A, bot.index, NOW + 5 * MINUTE))
        assertFalse(view(declined.state).botsOffered, "a cancelled countdown still claimed to be an offer")

        // Now the player gives up and fills the seats themselves.
        var byHand = decodeJoin(addBot(encode(declined.state), TOKEN_A, NOW + 7 * MINUTE)).state
        byHand = decodeJoin(addBot(encode(byHand), TOKEN_A, NOW + 7 * MINUTE)).state

        assertEquals(RoomPhase.STARTING, byHand.phase)
        assertFalse(view(byHand).botsOffered, "the room took credit for a bot the player added")
    }

    /**
     * And a declined room still closes, which is the other half of what this is for.
     *
     * Either outcome ends the wait and stops paying for it: a game, or a deleted room. The one
     * thing that must not happen is what used to — two people and a Durable Object, waiting.
     */
    @Test
    fun aRoomThatDeclinesIsStillSweptAtItsUsualHour() {
        val offered = alarm(lobbyOfTwo(), NOW + 5 * MINUTE).state
        val bot = offered.seats.first { it.isBot }
        val declined = decodeJoin(removeBot(encode(offered), TOKEN_A, bot.index, NOW + 5 * MINUTE))

        assertTrue(alarm(encode(declined.state), NOW + 11 * MINUTE).deleted, "the room outlived its sweep")
    }

    /**
     * One person alone is not offered a game, and that is design R1 rather than an oversight.
     *
     * A lone human against three bots is exactly what the device does offline for free, so
     * hosting it costs CPU and buys nothing. The wait still ends — the lobby sweep closes the
     * room — but it does not end by spending a Durable Object on a game that needed no server.
     */
    @Test
    fun oneManWaitingIsNotHandedThreeBots() {
        var state = newRoom("room-ALONE", seed = 7.0, difficulty = "easy", nowMs = NOW)
        state = encode(decodeJoin(joinRoom(state, TOKEN_A, "Ann", NOW)).state)
        state = encode(present(state, "0").state)

        val after = alarm(state, NOW + 5 * MINUTE)
        assertEquals(RoomPhase.LOBBY, after.state.phase, "a lone player was dealt a hosted game")
        assertTrue(alarm(encode(after.state), NOW + 11 * MINUTE).deleted, "and the room never closed")
    }

    /**
     * A seat held by somebody whose phone is in their pocket does not count.
     *
     * Dealing to them hands their hand to a bot on the seat grace half a minute later, which
     * is the same waste this exists to stop, wearing a costlier costume.
     */
    @Test
    fun aSeatWithNobodyLookingAtItIsNotSomebodyToDealTo() {
        var state = lobbyOfTwo()
        state = encode(present(state, "0").state) // Bob holds his seat; his socket is gone

        val after = alarm(state, NOW + 5 * MINUTE)
        assertEquals(RoomPhase.LOBBY, after.state.phase, "the room dealt to an absent player")
    }

    // ------------------------------------------------------------------ helpers

    /** Two humans seated and both connected, with two seats nobody is coming to fill. */
    private fun lobbyOfTwo(): String {
        var state = newRoom("room-WAIT", seed = 42.0, difficulty = "easy", nowMs = NOW)
        state = encode(decodeJoin(joinRoom(state, TOKEN_A, "Ann", NOW)).state)
        state = encode(decodeJoin(joinRoom(state, TOKEN_B, "Bob", NOW)).state)
        return encode(present(state, "0,1").state)
    }

    private fun view(state: RoomState): LobbyView =
        VintoJson.decodeFromString(LobbyView.serializer(), lobbyView(encode(state), NOW))

    private fun present(state: String, connectedCsv: String): LifecycleResult =
        VintoJson.decodeFromString(
            LifecycleResult.serializer(),
            updatePresence(state, connectedCsv, NOW),
        )

    private fun alarm(state: String, nowMs: Double): LifecycleResult =
        VintoJson.decodeFromString(LifecycleResult.serializer(), onAlarm(state, nowMs))

    private fun LifecycleResult.startsAtEpochMs(): Double? = state.startsAtEpochMs

    private fun encode(state: RoomState): String =
        VintoJson.encodeToString(RoomState.serializer(), state)

    private fun decodeJoin(json: String): JoinResult =
        VintoJson.decodeFromString(JoinResult.serializer(), json)

    private companion object {
        const val NOW = 1_000_000.0
        const val MINUTE = 60_000.0
        const val TOKEN_A = "token-ann"
        const val TOKEN_B = "token-bob"
    }
}
