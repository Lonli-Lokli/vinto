package game.vinto.room

import game.vinto.protocol.AnalyticsEvent
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How many people are playing at this moment, and in how many rooms.
 *
 * Asked for directly — *"I am interested to know max number of gaming same time"* — and it is
 * the one question an append-only event store cannot be made to answer by counting: a row per
 * arrival and a row per departure describe a shape nobody can add up without a window function,
 * and a sampled store loses rows besides. So the number is **written down** rather than derived,
 * as a gauge on the registry, which is the one place that holds every live room at once.
 *
 * **Written when something changes, not on a timer.** A minute alarm would cost 1,440 wakeups a
 * day to say "still nobody" on the days that are exactly why the number is wanted. Every arrival
 * and departure already touches the registry, and a maximum is always attained at a change — so
 * `max(humans)` over any window is exact, and an idle service writes nothing at all.
 *
 * The lease decides what counts as live, the same as it does for the public list: a room that
 * has stopped speaking is not a room with people in it, whatever its last touch claimed.
 */
class HowManyArePlayingNowTest {

    private val minute = 60_000.0
    private val t0 = 1_000_000_000.0

    @Test
    fun anIdleServiceIsHonestlyEmpty() {
        val live = liveAt(newRegistry(), t0)
        assertEquals(0, live.rooms, "no rooms")
        assertEquals(0, live.humans, "nobody")
    }

    @Test
    fun peopleInRoomsAreCountedAcrossAllOfThem() {
        var json = stateOf(minted("1,2,3,4,5,6", "ada"))
        val first = codeOf(json)
        json = touchRoom(json, first, humans = 3, seatsFilled = 4, startsAtEpochMs = 0.0, nowMs = t0)

        val second = mintRoomCode(json, "9,8,7,6,5,4", true, "Bo", "bo", t0)
        json = stateOf(VintoJson.decodeFromString(MintResult.serializer(), second))
        val other = VintoJson.decodeFromString(MintResult.serializer(), second).room!!.code
        json = touchRoom(json, other, humans = 2, seatsFilled = 4, startsAtEpochMs = 0.0, nowMs = t0)

        val live = liveAt(json, t0)
        assertEquals(2, live.rooms, "two rooms are open")
        assertEquals(5, live.humans, "three and two people are five people")
    }

    /** A room that has gone quiet is not a room with people in it, whatever it last said. */
    @Test
    fun aRoomPastItsLeaseCountsNobody() {
        var json = stateOf(minted("1,2,3,4,5,6", "ada"))
        json = touchRoom(json, codeOf(json), humans = 4, seatsFilled = 4, startsAtEpochMs = 0.0, nowMs = t0)

        assertEquals(4, liveAt(json, t0 + 9 * minute).humans, "inside the lease, still four")
        assertEquals(0, liveAt(json, t0 + 11 * minute).humans, "silent, so nobody")
        assertEquals(0, liveAt(json, t0 + 11 * minute).rooms, "and no room either")
    }

    /** And it goes out as the gauge the dashboard takes a maximum of. */
    @Test
    fun theGaugeIsAnEventWithTwoNumbersOnIt() {
        var json = stateOf(minted("1,2,3,4,5,6", "ada"))
        json = touchRoom(json, codeOf(json), humans = 3, seatsFilled = 4, startsAtEpochMs = 0.0, nowMs = t0)

        val point = VintoJson.decodeFromString(
            AnalyticsEvent.serializer(),
            playersLiveEvent(json, t0),
        )
        assertEquals(AnalyticsEvent.PlayersLive(rooms = 1, humans = 3), point)
    }

    private fun minted(bytes: String, source: String): MintResult =
        VintoJson.decodeFromString(
            MintResult.serializer(),
            mintRoomCode(newRegistry(), bytes, isPublic = true, hostNickname = "Ada", sourceId = source, nowMs = t0),
        )

    private fun stateOf(result: MintResult): String =
        VintoJson.encodeToString(RegistryState.serializer(), result.state)

    private fun codeOf(json: String): String =
        VintoJson.decodeFromString(RegistryState.serializer(), json).rooms.first().code

    private fun liveAt(json: String, at: Double): PlayersLive =
        VintoJson.decodeFromString(PlayersLive.serializer(), liveNow(json, at))
}
