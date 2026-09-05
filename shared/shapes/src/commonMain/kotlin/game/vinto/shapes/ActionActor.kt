package game.vinto.shapes

/**
 * Actions the game no longer offers, which a live door refuses and a replay still applies.
 *
 * There is exactly one, and the reason it is a list rather than a deletion is the frozen
 * corpus: 42 of the 50 recordings carry a `SET_COALITION_LEADER`, and `GameState`'s
 * `coalitionLeaderId` — which has no `@EncodeDefault(NEVER)` — is inside every recorded
 * state's canonical hash. Neither can be removed without moving hashes a second
 * implementation computed and nothing can recompute.
 *
 * It cannot live in `ActionValidator` either: `GameEngine.reduce` validates before it
 * dispatches, so a refusal there is a refusal on the replay path, and `CorpusReplayTest`
 * rejects all 42. It lives here, beside [actorId], because it is the same kind of rule — what
 * a door may accept — and because both doors have to read the same answer. A room and a solo
 * game that disagreed about which moves exist would be two games.
 *
 * Why the nomination went: it decided nothing. The round is scored against the **lowest**
 * coalition hand whoever holds it, `CoalitionSearch` scores the same, and every bot declares
 * before any coalition turn is played — so the planners already reach one target from the
 * same public claims. What it cost was a stall at the top of the final round and a hole in
 * the seat boundary: naming nobody, it slipped the door's `actorId` check entirely, so the
 * **Vinto caller** could nominate the coalition's leader.
 */
val GameAction.retired: Boolean
    get() = this is GameAction.SetCoalitionLeader

/**
 * Who an action claims to be from.
 *
 * The seat boundary is checked against this, both by the Durable Object (which maps a token
 * to a seat and refuses anything naming another player) and by the local session (which knows
 * only one seat and refuses everything else). Those two are the same rule, so they read the
 * same function: a room and a solo game that disagreed about who may act would be two games.
 *
 * `null` for the few actions that name nobody — setting the coalition leader, changing the
 * difficulty, the debug hooks — which the validator checks alone.
 */
// Detekt reads this as complex; what it is measuring is the size of the action union, not the
// difficulty of the code. An exhaustive `when` with no `else` is the point: a new action
// becomes a compile error here, which is where a missing seat check would otherwise hide.
@Suppress("CyclomaticComplexMethod")
val GameAction.actorId: String?
    get() = when (this) {
        is GameAction.DrawCard -> payload.playerId
        is GameAction.PlayDiscard -> payload.playerId
        is GameAction.SwapCard -> payload.playerId
        is GameAction.DiscardCard -> payload.playerId
        is GameAction.UseCardAction -> payload.playerId
        is GameAction.SelectActionTarget -> payload.playerId
        is GameAction.ConfirmPeek -> payload.playerId
        is GameAction.SkipPeek -> payload.playerId
        is GameAction.ExecuteJackSwap -> payload.playerId
        is GameAction.SkipJackSwap -> payload.playerId
        is GameAction.ExecuteQueenSwap -> payload.playerId
        is GameAction.SkipQueenSwap -> payload.playerId
        is GameAction.DeclareKingAction -> payload.playerId
        is GameAction.ParticipateInTossIn -> payload.playerId
        is GameAction.PlayerTossInFinished -> payload.playerId
        is GameAction.FinishTossInPeriod -> payload.initiatorId
        is GameAction.CallVinto -> payload.playerId
        is GameAction.ProcessAiTurn -> payload.playerId
        is GameAction.PeekSetupCard -> payload.playerId
        is GameAction.FinishSetup -> payload.playerId
        is GameAction.SetCoalitionLeader -> null
        is GameAction.DeclareCards -> payload.playerId
        // Nobody's move: the engine ends a round that cannot be played on.
        is GameAction.EndRound -> null
        is GameAction.UpdateDifficulty -> null
        is GameAction.SetNextDrawCard -> null
        is GameAction.SwapHandWithDeck -> null
        is GameAction.Empty -> null
    }
