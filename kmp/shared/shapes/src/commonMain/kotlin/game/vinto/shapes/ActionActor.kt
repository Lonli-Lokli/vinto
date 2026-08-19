package game.vinto.shapes

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
        is GameAction.UpdateDifficulty -> null
        is GameAction.SetNextDrawCard -> null
        is GameAction.SwapHandWithDeck -> null
        is GameAction.Empty -> null
    }
