package game.vinto.protocol

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The privacy rule, held by the compiler's own view of the types rather than by review.
 *
 * HOSTING.md §6c binds this zone to no cookies, no identifiers and nothing that follows a person. The
 * cheap way to keep that promise is to make the promise unrepresentable to break: every field
 * of every analytics event is a number, a boolean or an enum, so a room code, a nickname, a
 * seat token or an IP has nowhere to sit.
 *
 * This walks the sealed hierarchy by reflection instead of listing the events, because the
 * failure it exists to catch is the *next* field somebody adds — a `roomId: String` on a new
 * event would pass every other test in the repository and quietly publish a shared secret
 * into a store a dashboard reads.
 */
class AnalyticsPrivacyTest {

    private val numbers = setOf(
        Int::class.javaPrimitiveType,
        Double::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
    )

    @Test
    fun noEventCanCarryAFreeString() {
        val events = AnalyticsEvent::class.sealedSubclasses
        assertTrue(events.isNotEmpty(), "the sealed hierarchy has no cases — this test would pass vacuously")

        val offenders = mutableListOf<String>()
        for (event in events) {
            // Declared *fields*, so `name` — a getter returning a literal, and the one String
            // here that is a discriminator rather than data — is not among them.
            for (field in event.java.declaredFields) {
                if (field.isSynthetic || Modifier.isStatic(field.modifiers)) continue

                val type = field.type
                if (type.isEnum || type in numbers) continue

                offenders += if (type == java.lang.String::class.java) {
                    "${event.simpleName}.${field.name} is a String — use an enum, so a room " +
                        "code or a nickname cannot be put here"
                } else {
                    "${event.simpleName}.${field.name}: ${type.name} is neither a number nor an enum"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail("analytics events may only carry numbers, booleans and enums:\n  " + offenders.joinToString("\n  "))
        }
    }

    /**
     * And the rendered point carries nothing a person could be recognised by.
     *
     * The check above is about what *can* be declared; this is about what actually goes out,
     * which is the thing an operator would see in the store.
     *
     * **The rule got wider when the schema did, and it did not get weaker.** Points used to be
     * positional, so every blob was an enum label and the check could be "is this string in the
     * vocabulary". They are self-describing now — the shared portfolio schema, so one dashboard
     * can draw every game from configuration — which means a blob may also be the game's name,
     * the event's name, a FIELD name, or a value rendered from a number or a boolean.
     *
     * So the check is the same claim stated for the wider alphabet: every blob is one of those
     * six things, and none of them can carry text a person wrote. The structural half — that
     * `AnalyticsEvent` has no `String` field to carry it in the first place — is
     * [nothingIdentifyingIsRepresentable] above, which is what makes this enumerable at all.
     */
    @Test
    fun everyRenderedBlobIsAnEnumLabelOrTheEventName() {
        val samples = listOf(
            AnalyticsEvent.RoomCreated(listed = true, difficulty = Difficulty.HARD),
            AnalyticsEvent.SeatFilled(humans = 2, bots = 2, byBot = false),
            AnalyticsEvent.SeatVacated(humans = 1, bots = 2, grace = true),
            AnalyticsEvent.BotTookOver(humans = 1),
            AnalyticsEvent.Reconnected(awayMs = 4_000.0),
            AnalyticsEvent.RoundStart(humans = 2, bots = 2, roundNumber = 3),
            AnalyticsEvent.RoundEnd(
                actions = 44,
                durationMs = 91_000.0,
                endedBy = RoundEnding.VINTO_CALLED,
                callerWon = true,
            ),
            AnalyticsEvent.SessionEnded(reason = SessionEnding.PLAYED_OUT, rounds = 3, durationMs = 600_000.0),
            AnalyticsEvent.SoloRound(finished = true, difficulty = Difficulty.EASY, turns = 30, durationMs = 60_000.0),
            AnalyticsEvent.Lesson(finished = false, reachedStage = 7, durationMs = 120_000.0),
        )

        // Every case is covered, so adding one without a sample here fails rather than
        // silently going unchecked.
        assertTrue(
            samples.map { it::class }.toSet().size == AnalyticsEvent::class.sealedSubclasses.size,
            "a new event type has no sample: this test would not see what it emits",
        )

        val vocabulary = buildSet {
            addAll(Difficulty.entries.map { it.name })
            addAll(RoundEnding.entries.map { it.name })
            addAll(SessionEnding.entries.map { it.name })
            addAll(listOf("true", "false"))
            add(GAME)
            addAll(samples.map { it.name })
        }

        // Tag and measure NAMES — the words that make the schema self-describing. A closed list
        // rather than a pattern, so a new field arrives in this test before it arrives in the
        // store, and somebody has to look at it.
        val fields = setOf(
            "difficulty", "listed", "humans", "bots", "by_bot", "grace", "ended_by", "caller_won",
            "reason", "finished", "away_ms", "round_number", "duration_ms", "actions", "rounds",
            "turns", "reached_stage", "wall_ms", "requests",
        )

        for (sample in samples) {
            val point = sample.toDataPoint(Cost(wallMs = 12.0, requests = 1.0))
            assertTrue(
                point.indexes == listOf(GAME),
                "the index must be the game, so one dashboard can group by it: ${point.indexes}",
            )
            assertTrue(point.blobs[1] == sample.name, "blob2 must be the event name: ${point.blobs}")
            for (blob in point.blobs) {
                assertTrue(
                    blob.isEmpty() || blob in vocabulary || blob in fields || blob.toDoubleOrNull() != null,
                    "'$blob' on ${sample.name} is not a name, an enum label or a number — " +
                        "only closed vocabularies may be written",
                )
            }
        }
    }

    /** Cost rides on server events, and its absence is representable for client ones. */
    @Test
    fun costIsCarriedWhenItIsKnown() {
        val withCost = AnalyticsEvent.RoundEnd(30, 60_000.0, RoundEnding.DECK_EXHAUSTED, false)
            .toDataPoint(Cost(wallMs = 1_600.0, requests = 12.0))
        val without = AnalyticsEvent.SoloRound(true, Difficulty.EASY, 30, 60_000.0).toDataPoint()

        // Read by NAME rather than by slot, which is the whole reason the schema changed: a test
        // that asserts on `doubles[3]` passes for a point whose fields moved under it.
        val named = withCost.blobs.drop(8).zip(withCost.doubles.drop(1)).toMap()
        assertTrue(named["wall_ms"] == 1_600.0, "wall time is not on the point: $withCost")
        assertTrue(named["requests"] == 12.0, "the request count is not on the point: $withCost")

        assertTrue(
            without.doubles.first() == 1.0 && without.blobs.none { it == "wall_ms" || it == "requests" },
            "a client event should carry its sample rate and no cost: $without",
        )
    }
}
