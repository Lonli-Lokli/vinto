package game.vinto.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How long a test that plays a whole game is allowed to take.
 *
 * `runTest` defaults to **sixty seconds of wall clock**, which is generous on the JVM — the
 * whole of `RecordingRoundTripTest` runs in 9 s there — and is not generous on Kotlin/Native,
 * where an MCTS game on an iOS simulator runs several times slower. Both suites that play a
 * game out have now been over that line, one of them twice, and each time the symptom was an
 * `UncompletedCoroutinesError` on `kmp-ios` and nowhere else.
 *
 * So the budget lives here rather than in a companion object per suite: it was a rule written
 * in `TRAPS.md` §7 and then not applied to the very next test written in the same session,
 * which is what a rule that has no home does.
 *
 * **This number is a CI budget on the slowest target, not a claim about the code.** Nothing
 * here should take minutes; the point is that a slow *machine* must fail the build for a real
 * reason rather than for a deadline.
 */
internal val WHOLE_GAME: Duration = 5.minutes
