package game.vinto.room

import kotlin.test.Test

/** `ConferWindowTest`'s bot-called round, played whole. */
class ConferWindowDeepTest {

    @Test
    fun aBotsVintoCallStillOpensTheWindowBeforeAnyCoalitionTurn() =
        ConferWindowTest().aBotsVintoCallStillOpensTheWindowBeforeAnyCoalitionTurnRun()
}
