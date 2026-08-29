package game.vinto.protocol

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The privacy rule, held by the compiler's own view of the types rather than by review.
 *
 * §6c binds this zone to no cookies, no identifiers and nothing that follows a person. The
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
     * And the rendered point carries no strings beyond the event name and its enum labels.
     *
     * The check above is about what *can* be declared; this is about what actually goes out,
     * which is the thing an operator would see in the store.
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
                turns = 44,
                durationMs = 91_000.0,
                endedBy = RoundEnding.VINTO_CALLED,
                callerWon = true
            ),
            AnalyticsEvent.SessionEnded(reason = SessionEnding.PLAYED_OUT, rounds = 3, durationMs = 600_000.0),
            AnalyticsEvent.Funnel(step = FunnelStep.INVITE_SHARED, surface = Surface.ONLINE),
            AnalyticsEvent.SoloRound(finished = true, difficulty = Difficulty.EASY, turns = 30, durationMs = 60_000.0),
            AnalyticsEvent.Lesson(finished = false, reachedStage = 7, durationMs = 120_000.0),
            AnalyticsEvent.Failure(kind = FailureKind.STAGE_STALLED, surface = Surface.SOLO),
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
            addAll(FunnelStep.entries.map { it.name })
            addAll(Surface.entries.map { it.name })
            addAll(FailureKind.entries.map { it.name })
        }

        for (sample in samples) {
            val point = sample.toDataPoint(Cost(wallMs = 12.0, requests = 1.0))
            assertTrue(point.indexes == listOf(sample.name), "the index must be the event name: ${point.indexes}")
            for (blob in point.blobs) {
                assertTrue(
                    blob in vocabulary,
                    "'$blob' on ${sample.name} is not an enum label — only closed vocabularies may be written",
                )
            }
        }
    }

    /** Cost rides on server events, and its absence is representable for client ones. */
    @Test
    fun costIsCarriedWhenItIsKnown() {
        val withCost = AnalyticsEvent.RoundEnd(30, 60_000.0, RoundEnding.DECK_EXHAUSTED, false)
            .toDataPoint(Cost(wallMs = 1_600.0, requests = 12.0))
        val without = AnalyticsEvent.Funnel(FunnelStep.APP_OPENED, Surface.MENU).toDataPoint()

        assertTrue(withCost.doubles.takeLast(2) == listOf(1_600.0, 12.0), "cost is not on the point: $withCost")
        assertTrue(without.doubles.size == 1, "a client event should carry only its sample rate: $without")
    }
}
