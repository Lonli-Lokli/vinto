package game.vinto.engine

import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rules the impersonation sweep cannot reach: the ones about *what* a player may do rather
 * than *whether it is their turn*.
 *
 * Each case is posed against a state taken from the corpus — a position that genuinely arose
 * in a recorded game — rather than one hand-assembled to make the rule fire. A fabricated
 * state proves the branch is reachable; a real one proves the rule bites where it matters.
 */
class ValidatorRulesTest {

    private val recordings: List<GameRecording> =
        File("../../../fixtures/recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    /** Every state the corpus passes through, so a rule can be posed where it actually applies. */
    private fun states(): Sequence<GameState> = sequence {
        for (recording in recordings) {
            var state = recording.initialState
            yield(state)
            for (entry in recording.actions) {
                state = (GameEngine.reduce(state, entry.action) as ReduceResult.Success).state
                yield(state)
            }
        }
    }

    private fun invalid(state: GameState, action: GameAction): Boolean =
        ActionValidator.validate(state, action) is Validation.Invalid

    @Test
    fun coalitionMembersCannotTargetTheVintoCaller() {
        // The rule that makes calling Vinto a commitment rather than a free option: once the
        // final round starts, the coalition may not touch the caller's cards.
        val positions = states().filter {
            it.phase == GamePhase.FINAL &&
                it.vintoCallerId != null &&
                it.coalitionLeaderId != null &&
                it.pendingAction != null &&
                (it.subPhase == GameSubPhase.AWAITING_ACTION || it.subPhase == GameSubPhase.SELECTING)
        }.take(25).toList()

        assertTrue(positions.isNotEmpty(), "corpus contains no coalition action position")

        for (state in positions) {
            val actor = state.pendingAction!!.playerId
            if (actor == state.vintoCallerId) continue

            val action = GameAction.SelectActionTarget(
                SelectActionTargetPayload.Positional(
                    playerId = actor,
                    targetPlayerId = state.vintoCallerId!!,
                    position = 0,
                ),
            )
            assertTrue(invalid(state, action), "coalition member was allowed to target the caller")
        }
    }

    @Test
    fun aFailedTossInEndsParticipationForTheRound() {
        val positions = states().filter { it.roundFailedAttempts.isNotEmpty() }.take(10).toList()
        assertTrue(positions.isNotEmpty(), "corpus contains no failed toss-in attempt")

        for (state in positions) {
            val offender = state.roundFailedAttempts.first().playerId
            val action = GameAction.ParticipateInTossIn(
                ParticipateInTossInPayload(playerId = offender, positions = listOf(0)),
            )
            assertTrue(invalid(state, action), "a player tossed in again after failing")
        }
    }

    @Test
    fun vintoCannotBeCalledTwice() {
        val positions = states().filter { it.vintoCallerId != null }.take(10).toList()
        assertTrue(positions.isNotEmpty(), "corpus contains no Vinto call")

        for (state in positions) {
            val current = state.players[state.currentPlayerIndex].id
            assertTrue(
                invalid(state, GameAction.CallVinto(PlayerIdPayload(current))),
                "Vinto was allowed to be called twice",
            )
        }
    }

    @Test
    fun setupPeeksAreBoundedAndNotRepeatable() {
        val setup = states().first { it.phase == GamePhase.SETUP }
        val player = setup.players.first()

        // A position already peeked cannot be peeked again...
        val known = player.knownCardPositions.firstOrNull()
        if (known != null) {
            assertTrue(
                invalid(setup, GameAction.PeekSetupCard(PositionPayload(player.id, known))),
                "the same setup card was peeked twice",
            )
        }

        // ...nor can a position that does not exist.
        assertTrue(
            invalid(setup, GameAction.PeekSetupCard(PositionPayload(player.id, player.cards.size))),
            "a card outside the hand was peeked",
        )

        // And setup cannot be finished before two cards have been seen.
        val unpeeked = setup.players.firstOrNull { it.knownCardPositions.size < 2 }
        if (unpeeked != null) {
            assertTrue(
                invalid(setup, GameAction.FinishSetup(PlayerIdPayload(unpeeked.id))),
                "setup finished without the required peeks",
            )
        }
    }

    @Test
    fun aBotResolvingATossedInJackOrQueenCanActuallyFinishIt() {
        // `selecting` is where a bot sits while it works through a tossed-in action card.
        // CONFIRM_PEEK, DECLARE_KING_ACTION and SELECT_ACTION_TARGET all allow it; the two
        // swaps did not, which left a bot that tossed in a Jack able to choose both targets
        // and then unable to do anything at all. A state with no legal move stops the game,
        // which is why this is a rule test and not a tidiness one.
        val state = states().first {
            it.subPhase == GameSubPhase.SELECTING &&
                it.pendingAction?.card?.rank in listOf(Rank.JACK, Rank.QUEEN)
        }
        val actor = state.pendingAction!!.playerId

        // Posed from a real position, but with the two targets the swap needs — the corpus
        // reaches the sub-phase far more often than it reaches it with targets chosen.
        val ready = state.copy(
            pendingAction = state.pendingAction!!.copy(
                targets = listOf(
                    ActionTarget(playerId = state.players[0].id, position = 0),
                    ActionTarget(playerId = state.players[1].id, position = 0),
                ),
            ),
        )

        val finish = if (ready.pendingAction!!.card.rank == Rank.JACK) {
            GameAction.SkipJackSwap(PlayerIdPayload(actor))
        } else {
            GameAction.SkipQueenSwap(PlayerIdPayload(actor))
        }

        assertTrue(
            !invalid(ready, finish),
            "a bot resolving a tossed-in ${ready.pendingAction!!.card.rank.serialName} could not " +
                "finish: ${(ActionValidator.validate(ready, finish) as? Validation.Invalid)?.reason}",
        )

        // The rule it must still enforce: two targets, from two different players.
        val sameSeat = ready.copy(
            pendingAction = ready.pendingAction!!.copy(
                targets = ready.pendingAction!!.targets.map {
                    it.copy(playerId = ready.players[0].id)
                },
            ),
        )
        assertTrue(invalid(sameSeat, finish), "a swap between one player's own cards was allowed")
    }

    @Test
    fun unknownPlayersAreRejectedEverywhere() {
        // A client inventing a player id is the simplest attack there is.
        for (state in states().take(200)) {
            val ghost = "not-a-player"
            assertTrue(invalid(state, GameAction.DrawCard(PlayerIdPayload(ghost))))
            assertTrue(invalid(state, GameAction.ParticipateInTossIn(
                ParticipateInTossInPayload(ghost, listOf(0)),
            )) || state.activeTossIn == null)
        }
    }
}
