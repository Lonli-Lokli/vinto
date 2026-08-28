package game.vinto.bot

/**
 * Reading a plan back out of the search tree, ported from
 * `legacy-web/packages/bot/src/lib/mcts-action-planning.ts`.
 *
 * Some decisions arrive at the engine in two parts. Taking an action card from the discard
 * commits the bot to playing it, but the targets are asked for afterwards; a King declares a
 * rank first and is asked what to point the declared action at second. If the bot re-ran the
 * search at that second question it could answer it in a way that contradicts why it made the
 * first choice — take a Jack to move a Joker, then use the Jack for something else entirely.
 *
 * So the plan is extracted at the moment of the first decision, from the child the search
 * actually committed its visits to, and cached until the follow-up question arrives.
 */
fun extractActionPlan(node: MctsNode): BotActionDecision? {
    val actionChild = node.selectMostVisitedChild() ?: return null
    val move = actionChild.move ?: return null
    if (move.targets.isEmpty()) return null

    return BotActionDecision(
        targets = move.targets.map { BotActionTarget(it.playerId, it.position) },
        shouldSwap = move.shouldSwap,
        declaredRank = move.declaredRank,
    )
}

/** Whether a move is one whose follow-up targets need to be settled up front. */
fun shouldExtractActionPlan(move: MctsMove): Boolean = when (move.type) {
    MctsMoveType.TAKE_DISCARD -> move.actionCard?.actionText != null
    MctsMoveType.USE_ACTION -> move.declaredRank != null || move.targets.isNotEmpty()
    else -> false
}
