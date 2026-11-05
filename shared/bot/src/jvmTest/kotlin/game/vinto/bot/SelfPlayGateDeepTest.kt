package game.vinto.bot

import game.vinto.shapes.Difficulty
import kotlin.test.Test

/**
 * The gate: `SelfPlayGateTest`'s questions over twelve `moderate` games, and its replay at
 * `moderate`.
 *
 * Twelve games are four minutes and the replay over twenty seconds, so they run in the deep
 * suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite asks the same
 * questions of one `easy` game.
 */
class SelfPlayGateDeepTest {

    @Test
    fun botsPlayCompleteGamesAndEveryActionTheyProposeIsLegal() {
        SelfPlayGateTest().botsPlayCompleteGamesAndEveryActionTheyProposeIsLegalOver(
            SelfPlayGateTest.GATE_SEEDS,
            Difficulty.MODERATE,
        )
    }

    @Test
    fun aSeedReplaysToTheSameGame() {
        SelfPlayGateTest().aSeedReplaysToTheSameGameAt(Difficulty.MODERATE)
    }
}
