package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A game that survives being closed.
 *
 * The case worth testing is not "it writes a file" — it is that what comes back is the round
 * you were in the middle of, down to the cards you had looked at. A save that restores the
 * deal but not your memory of it has lost the game while appearing to keep it.
 */
class LocalGameTest {

    private suspend fun LocalGame.peekTwoAndStart() {
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(playerId)))
        save()
    }

    @Test
    fun aGameInProgressComesBackWhereItWasLeft() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 99L, difficulty = Difficulty.EASY)

        game.peekTwoAndStart()
        game.session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        game.session.dispatch(GameAction.DrawCard(PlayerIdPayload(game.playerId)))
        game.save()

        val resumed = LocalGame.resume(vault)!!

        assertEquals(Difficulty.EASY, resumed.difficulty)
        assertEquals(
            game.session.view.value.discardTop,
            resumed.session.view.value.discardTop,
        )
        assertEquals(
            game.session.view.value.discardCount,
            resumed.session.view.value.discardCount,
        )
        assertEquals(
            Rank.NINE,
            (resumed.session.view.value.pendingAction?.card as? game.vinto.engine.CardView.Visible)
                ?.card?.rank,
            "the card you had drawn is still in your hand",
        )
        assertEquals(
            listOf(0, 1),
            resumed.session.view.value.players.first { it.id == resumed.playerId }.knownCardPositions,
            "and the cards you had looked at are still ones you have looked at",
        )
    }

    @Test
    fun aResumedGameIsStillPlayable() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 4L, difficulty = Difficulty.EASY)
        game.peekTwoAndStart()

        val resumed = LocalGame.resume(vault)!!
        val table = tableFor(resumed.session.view.value)

        assertEquals("Your turn", table.prompt)
        val drawn = resumed.session.dispatch(
            (table.choices.first { it.label == Label.DrawCard }.move as Move.Send).action,
        )
        assertNull(drawn, "and the engine takes the move: $drawn")
    }

    @Test
    fun abandoningAGameLeavesNothingToComeBackTo() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 1L, difficulty = Difficulty.EASY)
        game.peekTwoAndStart()

        game.abandon()

        assertNull(LocalGame.resume(vault), "there is nothing saved")
    }

    /** Points carry between rounds; that is what makes it a game rather than a deal. */
    @Test
    fun finishingARoundCarriesItsPointsIntoTheNext() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 21L, difficulty = Difficulty.EASY)
        game.peekTwoAndStart()

        // End the round the quick way: draw, throw it away, then call Vinto in the window
        // that opens — which is where the rules put the call.
        game.session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        game.session.dispatch(GameAction.DrawCard(PlayerIdPayload(game.playerId)))
        game.session.dispatch(GameAction.DiscardCard(PlayerIdPayload(game.playerId)))
        val table = tableFor(game.session.view.value)
        game.session.dispatch((table.choices.first { it.label == Label.CallVinto }.move as Move.Send).action)

        assertTrue(game.session.isOver, "the round finished")
        assertEquals(1, game.round)
        assertTrue(game.standings.isEmpty(), "nothing is banked until the next deal")

        game.nextRound()

        assertEquals(2, game.round)
        assertEquals(FOUR_SEATS, game.standings.size, "every seat scored the round")
        assertTrue(game.standings.values.any { it != 0 }, "and somebody gained or lost")
    }

    @Test
    fun eachRoundIsADifferentDeal() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 7L, difficulty = Difficulty.EASY)
        val first = game.session.view.value.gameId

        game.nextRound()

        assertNotEquals(first, game.session.view.value.gameId, "a new round is a new deal")
    }

    /** A save this build cannot read is thrown away rather than crashing the app. */
    @Test
    fun anUnreadableSaveIsDiscarded() {
        val vault = MemoryVault()
        vault.write("vinto.local.game", "{ this is not a saved game")

        assertNull(vault.loadGame())
        assertNull(vault.read("vinto.local.game"), "and it is not left behind to fail again")
    }

    private companion object {
        const val FOUR_SEATS = 4
    }

    /**
     * The opening deal is animated only for a table that was actually just dealt: a new
     * game and each next round, never a resumed one — replaying the deal for a round that
     * was mid-game when the app closed would animate an event that is not happening.
     */
    @Test
    fun onlyAFreshDealIsFreshlyDealt() = runTest {
        val vault = MemoryVault()
        val game = LocalGame.start(vault, seed = 5L, difficulty = Difficulty.EASY)
        assertTrue(game.freshlyDealt, "a started game was just dealt")

        game.peekTwoAndStart()
        val resumed = LocalGame.resume(vault)!!
        assertTrue(!resumed.freshlyDealt, "a resumed round was not")

        resumed.nextRound()
        assertTrue(resumed.freshlyDealt, "the next round is dealt in front of the player")
    }
}
