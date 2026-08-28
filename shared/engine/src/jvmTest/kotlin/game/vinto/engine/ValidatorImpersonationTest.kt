package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameRecording
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The validator is the anti-cheat boundary, so the property that matters is what it
 * *refuses* — and the corpus alone can never show that, because every action in it was legal
 * when it was recorded. `CorpusReplayTest` passing with the validator live proves only that
 * nothing legal is rejected.
 *
 * This asserts the other half, and does it over real states rather than a handful of
 * hand-built ones: replay every recording, and at each step take the action that genuinely
 * happened and re-attribute it to **every other player at the table**. Each of those is an
 * attempt to act out of turn, in a game position that actually arose, and every one must be
 * rejected.
 *
 * That is roughly 13,900 states × 3 impostors — a scale of adversarial case no fixture set
 * would reach by hand, obtained for free from recordings that already exist.
 */
class ValidatorImpersonationTest {

    private val recordings: List<Pair<String, GameRecording>> =
        File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    /**
     * Re-attributes an action to [otherId], or returns null where the validator deliberately
     * does not bind the action to one seat.
     *
     * Toss-in actions are excluded on purpose: any player may toss in or declare themselves
     * ready, so another player's id there is legal rather than an impersonation. So are the
     * configuration and debug actions, which carry no actor at all.
     */
    private fun reattribute(action: GameAction, otherId: String): GameAction? = when (action) {
        is GameAction.DrawCard -> GameAction.DrawCard(PlayerIdPayload(otherId))
        is GameAction.PlayDiscard -> GameAction.PlayDiscard(PlayerIdPayload(otherId))
        is GameAction.DiscardCard -> GameAction.DiscardCard(PlayerIdPayload(otherId))
        is GameAction.UseCardAction -> GameAction.UseCardAction(PlayerIdPayload(otherId))
        is GameAction.ConfirmPeek -> GameAction.ConfirmPeek(PlayerIdPayload(otherId))
        is GameAction.SkipPeek -> GameAction.SkipPeek(PlayerIdPayload(otherId))
        is GameAction.CallVinto -> GameAction.CallVinto(PlayerIdPayload(otherId))
        is GameAction.ExecuteJackSwap -> GameAction.ExecuteJackSwap(PlayerIdPayload(otherId))
        is GameAction.SkipJackSwap -> GameAction.SkipJackSwap(PlayerIdPayload(otherId))
        is GameAction.ExecuteQueenSwap -> GameAction.ExecuteQueenSwap(PlayerIdPayload(otherId))
        is GameAction.SkipQueenSwap -> GameAction.SkipQueenSwap(PlayerIdPayload(otherId))
        is GameAction.ProcessAiTurn -> GameAction.ProcessAiTurn(PlayerIdPayload(otherId))
        is GameAction.SwapCard ->
            GameAction.SwapCard(action.payload.copy(playerId = otherId))

        is GameAction.DeclareKingAction ->
            GameAction.DeclareKingAction(action.payload.copy(playerId = otherId))

        is GameAction.SelectActionTarget -> GameAction.SelectActionTarget(
            when (val p = action.payload) {
                is SelectActionTargetPayload.Ace -> p.copy(playerId = otherId)
                is SelectActionTargetPayload.Positional -> p.copy(playerId = otherId)
            },
        )

        else -> null
    }

    @Test
    fun rejectsEveryActionAttributedToTheWrongPlayer() {
        val accepted = mutableListOf<String>()
        var attempts = 0

        for ((name, recording) in recordings) {
            var state = recording.initialState

            for ((index, entry) in recording.actions.withIndex()) {
                val actor = actorOf(entry.action)

                if (actor != null) {
                    attempts += probeImpostors(state, entry.action, actor, name, index, accepted)
                }

                state = (GameEngine.reduce(state, entry.action) as ReduceResult.Success).state
            }
        }

        // ~6,200 of the 13,900 actions bind to a seat — the rest are toss-in, which any
        // player may legitimately send — times three other players at the table.
        assertTrue(attempts > 15_000, "expected the whole corpus to be probed, tried $attempts")
        assertEquals(emptyList(), accepted, "the validator let another player act")
    }

    /** Tries the action from every other seat; returns how many attempts were made. */
    private fun probeImpostors(
        state: game.vinto.shapes.GameState,
        action: GameAction,
        actor: String,
        name: String,
        index: Int,
        accepted: MutableList<String>,
    ): Int {
        var attempts = 0
        for (other in state.players.map { it.id }.filter { it != actor }) {
            val impostor = reattribute(action, other) ?: continue
            attempts++
            if (GameEngine.reduce(state, impostor) !is ReduceResult.Failure && accepted.size < 10) {
                accepted += "$name#$index: ${action.type} accepted from $other"
            }
        }
        return attempts
    }

    /** The seat an action is bound to, or null where the validator binds it to no seat. */
    private fun actorOf(action: GameAction): String? = when (action) {
        is GameAction.DrawCard -> action.payload.playerId
        is GameAction.PlayDiscard -> action.payload.playerId
        is GameAction.DiscardCard -> action.payload.playerId
        is GameAction.UseCardAction -> action.payload.playerId
        is GameAction.ConfirmPeek -> action.payload.playerId
        is GameAction.SkipPeek -> action.payload.playerId
        is GameAction.CallVinto -> action.payload.playerId
        is GameAction.ExecuteJackSwap -> action.payload.playerId
        is GameAction.SkipJackSwap -> action.payload.playerId
        is GameAction.ExecuteQueenSwap -> action.payload.playerId
        is GameAction.SkipQueenSwap -> action.payload.playerId
        is GameAction.ProcessAiTurn -> action.payload.playerId
        is GameAction.SwapCard -> action.payload.playerId
        is GameAction.DeclareKingAction -> action.payload.playerId
        is GameAction.SelectActionTarget -> action.payload.playerId
        else -> null
    }
}
