package game.vinto.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ported from `legacy-web/packages/engine/src/lib/__tests__/purity-guard.test.ts`.
 *
 * The engine must be a pure function of (state, action). Anything ambient — a clock, a random
 * source, a generated id — makes a game unreplayable and breaks parity with the TypeScript,
 * which is the whole basis of the migration's verification. This fails the build if any
 * creeps back in.
 *
 * JVM-only, because it reads the source tree. The Kotlin patterns differ from the TypeScript
 * ones — there is no `Date.now()` here — so this is a translation of the *intent* rather than
 * of the regular expressions: same list of ambient sources, spelled the way Kotlin spells
 * them.
 */
class PurityGuardTest {

    private data class Forbidden(val name: String, val pattern: Regex)

    private val forbidden = listOf(
        Forbidden("kotlin.random.Random", Regex("""\bkotlin\.random\.Random\b|\bRandom\s*\.""")),
        Forbidden("Clock / TimeSource", Regex("""\bTimeSource\b|\bClock\b|\bcurrentTimeMillis\b""")),
        Forbidden("System.nanoTime", Regex("""\bnanoTime\s*\(""")),
        Forbidden("java.util.UUID", Regex("""\bUUID\b""")),
        Forbidden("Instant.now", Regex("""\bInstant\s*\.\s*now\b""")),
    )

    private val engineSources: List<File> =
        File("src/commonMain/kotlin/game/vinto/engine")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun findsEngineSourcesToScan() {
        // Guards the guard: a bad path would make every assertion below vacuous.
        assertTrue(
            engineSources.size > 10,
            "only found ${engineSources.size} engine sources; the scan path is wrong",
        )
    }

    @Test
    fun theReducerPathReachesForNothingAmbient() {
        for (rule in forbidden) {
            val offenders = engineSources
                .filter { rule.pattern.containsMatchIn(it.readText()) }
                .map { it.name }

            assertEquals(emptyList(), offenders, "${rule.name} appears in the reducer path")
        }
    }

    @Test
    fun randomnessComesFromGameStateRngState() {
        // The reshuffle in TossInUtils is the engine's only randomness consumer. If this
        // stops holding, the seeded generator has been bypassed somewhere.
        assertTrue(
            engineSources.any { Regex("""\brngState\b""").containsMatchIn(it.readText()) },
            "nothing reads rngState; the seeded generator has been bypassed",
        )
    }
}
