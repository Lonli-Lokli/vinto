package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PlayerView
import game.vinto.engine.discardTop
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.TargetType
import game.vinto.shapes.getCardConfig
import game.vinto.shapes.getCardName
import game.vinto.shapes.getCardShortDescription

/**
 * What the table is offering the player, right now.
 *
 * This is the whole of the game's interaction logic, and it lives here rather than in the
 * Compose tree for one reason: it is the part that can be *wrong*. Which button appears after
 * a Queen has peeked at its second card, whether a Jack may still be abandoned, what happens
 * when you tap your own card during a toss-in — these are rules questions with right answers,
 * and answering them inside composables would mean the only way to check them is to run a
 * phone. Here they are a pure function of the view, and a test can ask all of them in
 * milliseconds.
 *
 * The Compose layer above draws what this returns and sends back what the player touched. It
 * decides nothing.
 */
data class Table(
    /** One line telling the player what is being asked of them. */
    val prompt: String,
    /**
     * The smaller line under it: the rule that applies, or what the card in play does.
     *
     * Separate from [prompt] because they are read differently — the prompt is what you are
     * being asked, and this is what you need to know to answer it. Running them together is
     * how a heading turns into a paragraph nobody reads.
     */
    val detail: String? = null,
    /** Buttons, in the order they should be shown. */
    val choices: List<Choice> = emptyList(),
    /** Cards that can be touched, and what touching one does. */
    val taps: Map<CardRef, Move> = emptyMap(),
    /** Whole seats that can be touched — only an Ace names a player rather than a card. */
    val seatTaps: Map<String, Move> = emptyMap(),
    /** Ranks on offer, when a declaration is being asked for. */
    val ranks: List<RankChoice> = emptyList(),
    /**
     * The longer explanation, shown when the player asks for it.
     *
     * Every state has one. A card game's rules are the game — a Queen looks at two cards and
     * then *may* swap them, a wrong toss-in costs you a card, a King names a rank and plays
     * that rank's action — and a player who has to remember all of it before the first hand
     * is a player who stops. The words come from `CARD_CONFIGS`, which is the same copy the
     * web app shows, so the two teach the same game.
     */
    val help: String? = null,
    /** True when the player has nothing to do but watch. */
    val waiting: Boolean = false,
    /** The cards the screen may show face-up. See [revealedTo]. */
    val revealed: Set<CardRef> = emptySet(),
)

/** A card on the table: whose, and which slot. */
data class CardRef(val playerId: String, val position: Int)

/** A button. */
data class Choice(val label: String, val move: Move, val tone: Tone = Tone.NEUTRAL)

/** A rank the player may name. */
data class RankChoice(val rank: Rank, val move: Move)

/**
 * What kind of move a button is.
 *
 * Ported from the web app's `BUTTON_ACTION_VARIANTS`, colour for colour, and the reason is
 * stated in its own comment there: **muscle memory**. Green is always the move that gets on
 * with the game, blue always puts a card into a hand, slate always declines, orange is the
 * one that ends the round, amber is naming a rank. A player who has learned that on the web
 * should not have to learn it again here, and a player who learns it here gets it for free
 * when the same table appears online.
 *
 * It sits in the model rather than the theme because which kind a move *is* follows from the
 * rules, not from how it looks.
 */
enum class Tone {
    /** Get on with it: draw, play the action, continue. */
    PLAY,

    /** Put a card into a hand: swap, start the round. */
    KEEP,

    /** Decline: discard, skip, pass, go back. */
    NEUTRAL,

    /** Ends the round for everybody. */
    STAKES,

    /** Name a rank, and take the consequences if it is wrong. */
    DECLARE,
}

/**
 * What touching something does.
 *
 * Two kinds, and keeping them apart is what stops the UI inventing game state: [Send] goes to
 * the engine, [Ask] only changes what the screen is asking for next. A swap is the clearest
 * case — "swap" is not an action, it is the beginning of one, and the engine hears about it
 * once as `SWAP_CARD` with a position and possibly a declared rank.
 */
sealed interface Move {
    data class Send(val action: GameAction) : Move
    data class Ask(val question: Question) : Move
}

/**
 * A question the screen is putting to the player that the engine knows nothing about yet.
 *
 * Deliberately tiny and closed. Anything that outlives a single decision belongs in the game
 * state, where it can be replayed, rather than here.
 */
sealed interface Question {
    /** Nothing outstanding. */
    data object None : Question

    /** Which of my cards does the drawn one replace? */
    data object WhichSlot : Question

    /** Slot chosen — do I call the rank of the card going out, and gamble on it? */
    data class CallRank(val position: Int) : Question

}

private const val SETUP_PEEKS = 2
private const val TWO_TARGETS = 2

/**
 * Reads the table for [view]'s own seat.
 *
 * @param question what the screen is already asking, if anything. Fold it back in rather than
 *   keeping it inside a composable, so the whole of "what can I do" stays one expression.
 */
/**
 * Which cards a screen may turn over.
 *
 * The view carries more than this on purpose: it tells a seat everything that seat *knows*,
 * including the two cards it looked at during setup and any it has since remembered, because
 * the server has to be able to answer "what does this player know" for the bots and for a
 * reconnect. A screen that drew all of it would hand the player a permanent, perfect memory —
 * and remembering your own hand is most of what this game asks of you.
 *
 * So during play only what the *current action* has just revealed is shown, which is what the
 * web app does (`canSeePlayerCard` in `apps/vinto/src/app/components/logic`). The exceptions
 * are the two moments where the rules themselves turn cards over: setup, when you are told to
 * look at two of your own, and scoring, when every hand goes face-up. The coalition leader is
 * a third — they play their allies' hands, so they see them.
 */
fun revealedTo(view: PlayerView): Set<CardRef> {
    if (view.phase == GamePhase.SCORING) {
        return view.players.flatMapTo(mutableSetOf()) { seat ->
            seat.cards.indices.map { CardRef(seat.id, it) }
        }
    }

    if (view.phase == GamePhase.SETUP) {
        val me = view.players.firstOrNull { it.id == view.viewerId } ?: return emptySet()
        return me.knownCardPositions.mapTo(mutableSetOf()) { CardRef(me.id, it) }
    }

    // Whatever this action has been aimed at, for as long as it is running. The target's own
    // card is not read here: a peek does not travel on the pending action, it lands in the
    // knowledge the seat projection already reflects — the engine records that you now know
    // the card, and the projection turns it face-up for you. What is temporary is being
    // *shown* it, and that lasts exactly as long as the action does.
    val shown = view.pendingAction
        ?.takeIf { it.playerId == view.viewerId }
        ?.targets
        .orEmpty()
        .mapTo(mutableSetOf()) { CardRef(it.playerId, it.position) }

    // The coalition plays as one hand and its leader plays it, so they see all of it — the
    // Vinto caller's excepted, which is the whole point of the coalition rule.
    if (view.coalitionLeaderId == view.viewerId) {
        view.players
            .filter { it.id != view.vintoCallerId }
            .forEach { seat -> seat.cards.indices.forEach { shown += CardRef(seat.id, it) } }
    }

    return shown
}

@Suppress("ReturnCount")
fun tableFor(view: PlayerView, question: Question = Question.None): Table {
    val me = view.players.firstOrNull { it.id == view.viewerId }
        ?: return Table(prompt = "Watching", waiting = true)

    if (view.phase == GamePhase.SCORING) return scoringTable(view).showing(view)
    if (view.phase == GamePhase.SETUP) {
        return setupTable(view, me.id, me.knownCardPositions).showing(view)
    }

    // The coalition has to choose who plays its hand before the final round can run.
    val coalitionUndecided = view.vintoCallerId != null && view.coalitionLeaderId == null
    if (coalitionUndecided && view.viewerId != view.vintoCallerId) {
        return coalitionTable(view).showing(view)
    }

    tossInTable(view)?.let { return it.showing(view) }

    val pending = view.pendingAction
    if (pending != null && pending.playerId == view.viewerId) {
        return pendingTable(view, pending, question).showing(view)
    }

    val current = view.players.getOrNull(view.currentPlayerIndex)
    if (current?.id != view.viewerId || pending != null) {
        val who = current?.nickname ?: "Someone"
        return Table(prompt = "$who is playing", waiting = true).showing(view)
    }

    return turnStartTable(view).showing(view)
}

private fun Table.showing(view: PlayerView) = copy(revealed = revealedTo(view), help = helpFor(view))

/**
 * What the "?" explains, for whatever is happening.
 *
 * The card in play if there is one, since that is nearly always what the player is unsure
 * about, and otherwise the rule that governs the phase.
 */
private fun helpFor(view: PlayerView): String {
    val pending = (view.pendingAction?.card as? CardView.Visible)?.card
    if (pending != null && view.pendingAction?.playerId == view.viewerId) {
        val config = getCardConfig(pending.rank)
        val worth = "It is worth ${config.value}."
        return if (config.action == null) {
            "${config.name}. $worth It has no action, so it can only go into your hand or " +
                "onto the pile."
        } else {
            "${config.name}. $worth ${config.longDescription}. ${config.helpText}"
        }
    }

    return when {
        view.phase == GamePhase.SETUP ->
            "Look at two of your own cards and remember them — they go face-down again when " +
                "the round starts. Everything after that is memory."

        view.phase == GamePhase.SCORING ->
            "Every hand is face-up. The lowest total wins the round; the player who called " +
                "Vinto is scored against the best hand among everybody else."

        view.activeTossIn != null ->
            "Anybody holding a card of the same rank may throw it in, which gets it out of " +
                "their hand for nothing. Get the rank wrong and you take a penalty card, and " +
                "you cannot toss in again this round."

        view.vintoCallerId != null ->
            "Vinto has been called. Everybody else plays one last turn as a coalition, and " +
                "only their best hand counts against the caller's."

        else ->
            "Draw from the deck, or take the top of the discard pile if it is an action card " +
                "nobody has played. Keep what lowers your total; call Vinto when you think " +
                "yours is the lowest."
    }
}

// ---------------------------------------------------------------------------- setup

private fun setupTable(view: PlayerView, myId: String, peeked: List<Int>): Table {
    val hand = view.players.first { it.id == myId }.cards.indices

    if (peeked.size < SETUP_PEEKS) {
        val left = SETUP_PEEKS - peeked.size
        return Table(
            prompt = if (left == SETUP_PEEKS) {
                "Look at two of your cards"
            } else {
                "One more card to look at"
            },
            taps = hand.filterNot { it in peeked }.associate { position ->
                val peek = GameAction.PeekSetupCard(PositionPayload(myId, position))
                CardRef(myId, position) to Move.Send(peek)
            },
        )
    }

    // Everyone peeks before anyone plays, so this waits on the rest of the table — which for
    // a solo game is nobody, since the bots are dealt theirs.
    return Table(
        prompt = "Ready when you are",
        choices = listOf(
            Choice(
                "Start the round",
                Move.Send(GameAction.FinishSetup(PlayerIdPayload(myId))),
                Tone.KEEP,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------- turn start

private fun turnStartTable(view: PlayerView): Table {
    val me = view.viewerId
    val top = view.discardTop
    val choices = mutableListOf<Choice>()

    if (view.drawPileSize > 0) {
        choices += Choice("Draw Card", Move.Send(GameAction.DrawCard(PlayerIdPayload(me))), Tone.PLAY)
    }

    // Only an action card nobody has played yet can be taken, and taking it commits you to
    // playing it — it cannot go into your hand.
    if (top != null && top.actionText != null && !top.played) {
        choices += Choice(
            "Use ${getCardName(top.rank)}",
            Move.Send(GameAction.PlayDiscard(PlayerIdPayload(me))),
            Tone.PLAY,
        )
    }

    return Table(prompt = "Your turn", choices = choices)
}

// ---------------------------------------------------------------------------- the drawn card

private fun pendingTable(view: PlayerView, pending: PendingActionView, question: Question): Table =
    when {
        question is Question.WhichSlot -> whichSlotTable(view)
        question is Question.CallRank -> callRankTable(view, question.position)
        view.subPhase == GameSubPhase.CHOOSING -> choosingTable(view, pending)
        else -> targetingTable(view, pending)
    }

private fun choosingTable(view: PlayerView, pending: PendingActionView): Table {
    val me = view.viewerId
    val card = (pending.card as? CardView.Visible)?.card
    val choices = mutableListOf<Choice>()

    // An action card can be played instead of kept — but only if it has not been played
    // already, which is what makes a discard-pile action card takeable exactly once.
    if (card != null && card.actionText != null && !card.played) {
        choices += Choice(
            "Use Action",
            Move.Send(GameAction.UseCardAction(PlayerIdPayload(me))),
            Tone.PLAY,
        )
    }

    // A card taken off the discard pile must be played; it cannot be kept.
    if (pending.canGoToHand) {
        choices += Choice("Swap Cards", Move.Ask(Question.WhichSlot), Tone.KEEP)
        choices += Choice("Discard", Move.Send(GameAction.DiscardCard(PlayerIdPayload(me))))
    }

    val what = card?.let { "You drew the ${it.rank.serialName}" } ?: "You drew a card"
    val does = card?.let { getCardConfig(it.rank) }?.takeIf { it.action != null }?.longDescription
    return Table(prompt = what, detail = does, choices = choices)
}

private fun whichSlotTable(view: PlayerView): Table {
    val me = view.viewerId
    val hand = view.players.first { it.id == me }.cards.indices

    return Table(
        prompt = "Which card does it replace?",
        choices = listOf(Choice("Back", Move.Ask(Question.None))),
        taps = hand.associate { position ->
            CardRef(me, position) to Move.Ask(Question.CallRank(position))
        },
    )
}

private fun callRankTable(view: PlayerView, position: Int): Table {
    val me = view.viewerId

    return Table(
        prompt = "Name the card you are putting down?",
        detail = "Right plays its action; wrong costs you a card.",
        choices = listOf(
            Choice(
                "Just Swap (No Declaration)",
                Move.Send(GameAction.SwapCard(SwapCardPayload(me, position))),
                Tone.KEEP,
            ),
            Choice("Back", Move.Ask(Question.WhichSlot)),
        ),
        ranks = ALL_RANKS.map { rank ->
            RankChoice(rank, Move.Send(GameAction.SwapCard(SwapCardPayload(me, position, rank))))
        },
    )
}

// ---------------------------------------------------------------------------- aiming an action

private fun targetingTable(view: PlayerView, pending: PendingActionView): Table {
    val card = (pending.card as? CardView.Visible)?.card
    val borrowed = pending.declaredRank?.let { rank ->
        "The King declared a ${rank.serialName}: ${getCardShortDescription(rank)}"
    }

    return withBorrowed(borrowed) { when (pending.targetType) {
        TargetType.OWN_CARD -> peekTable(view, pending, "Look at one of your own cards", ownTaps(view))
        TargetType.OPPONENT_CARD ->
            peekTable(view, pending, "Look at one card of another player", opponentTaps(view))

        // A Jack swaps blind; a Queen looks first. Same two-target shape, different question
        // at the end, and neither may be skipped until both cards have been named.
        TargetType.SWAP_CARDS -> twoCardTable(
            view = view,
            pending = pending,
            prompt = "Choose two cards, from two different players",
            swap = GameAction.ExecuteJackSwap(PlayerIdPayload(view.viewerId)),
            leave = GameAction.SkipJackSwap(PlayerIdPayload(view.viewerId)),
        )

        TargetType.PEEK_THEN_SWAP -> twoCardTable(
            view = view,
            pending = pending,
            prompt = "Look at two cards, from two different players",
            swap = GameAction.ExecuteQueenSwap(PlayerIdPayload(view.viewerId)),
            leave = GameAction.SkipQueenSwap(PlayerIdPayload(view.viewerId)),
        )

        TargetType.DECLARE_ACTION -> declareTable(view, pending)
        TargetType.FORCE_DRAW -> forceDrawTable(view)

        null -> Table(
            prompt = card?.let { "The ${it.rank.serialName} is waiting" } ?: "Waiting",
            choices = listOf(giveUp(view.viewerId)),
        )
    } }
}

/**
 * Adds the King's borrowed action to whatever the table is asking.
 *
 * A King performs another rank's action, so the next question belongs to a card nobody
 * played. Without naming it, "choose two cards from two different players" arrives with no
 * explanation — the Queen it is imitating was never on the table.
 */
private inline fun withBorrowed(borrowed: String?, build: () -> Table): Table {
    val table = build()
    return if (borrowed == null) table else table.copy(detail = borrowed)
}

/**
 * Putting a pending card down unplayed.
 *
 * `CONFIRM_PEEK` is what the engine offers for this: it marks the card played, discards it and
 * opens the toss-in window. It is the exit from any half-aimed action, which matters because
 * some actions are legal to start and impossible to aim — a peek-own by a player who has
 * already read their whole hand has nowhere to look, and without an exit the game stops.
 */
private fun giveUp(me: String) = Choice("Put it down", Move.Send(GameAction.ConfirmPeek(PlayerIdPayload(me))))

private fun peekTable(
    view: PlayerView,
    pending: PendingActionView,
    prompt: String,
    taps: Map<CardRef, Move>,
): Table = if (pending.targets.isEmpty()) {
    Table(prompt = prompt, choices = listOf(giveUp(view.viewerId)), taps = taps)
} else {
    Table(
        prompt = "Remember it",
        choices = listOf(
            Choice(
                "Done",
                Move.Send(GameAction.ConfirmPeek(PlayerIdPayload(view.viewerId))),
                Tone.PLAY,
            ),
        ),
    )
}

private fun twoCardTable(
    view: PlayerView,
    pending: PendingActionView,
    prompt: String,
    swap: GameAction,
    leave: GameAction,
): Table = if (pending.targets.size < TWO_TARGETS) {
    Table(prompt = prompt, choices = listOf(giveUp(view.viewerId)), taps = anyTaps(view, pending))
} else {
    Table(
        prompt = "Swap them?",
        choices = listOf(
            Choice("Swap Cards", Move.Send(swap), Tone.PLAY),
            Choice("Leave them", Move.Send(leave)),
        ),
    )
}

private fun declareTable(view: PlayerView, pending: PendingActionView): Table =
    if (pending.targets.isEmpty()) {
        Table(
            prompt = "Choose any card",
            choices = listOf(giveUp(view.viewerId)),
            taps = anyTaps(view, pending),
        )
    } else {
        Table(
            prompt = "Say what it is, and play that card's action",
            choices = listOf(giveUp(view.viewerId)),
            ranks = ALL_RANKS.map { rank ->
                RankChoice(
                    rank,
                    Move.Send(
                        GameAction.DeclareKingAction(DeclareKingActionPayload(view.viewerId, rank)),
                    ),
                )
            },
        )
    }

/** The only action that names a player rather than a card. */
private fun forceDrawTable(view: PlayerView): Table = Table(
    prompt = "Who draws a card?",
    choices = listOf(giveUp(view.viewerId)),
    seatTaps = view.players.filter { it.id != view.viewerId }.associate { seat ->
        seat.id to Move.Send(
            GameAction.SelectActionTarget(
                SelectActionTargetPayload.Ace(view.viewerId, seat.id),
            ),
        )
    },
)

private fun ownTaps(view: PlayerView): Map<CardRef, Move> {
    val me = view.viewerId
    return view.players.first { it.id == me }.cards.indices.associate { position ->
        CardRef(me, position) to positional(me, me, position)
    }
}

private fun opponentTaps(view: PlayerView): Map<CardRef, Move> {
    val me = view.viewerId
    return view.players.filter { it.id != me }.flatMap { seat ->
        seat.cards.indices.map { position ->
            CardRef(seat.id, position) to positional(me, seat.id, position)
        }
    }.toMap()
}

/**
 * Every card on the table, minus the ones this action has already claimed.
 *
 * Jack and Queen both take two cards **from two different players**, so once one is chosen
 * the rest of that player's hand stops being a legal target. Leaving them tappable would
 * offer a move the engine refuses, which reads to a player as the game being broken rather
 * than as them having misremembered a rule.
 */
private fun anyTaps(view: PlayerView, pending: PendingActionView): Map<CardRef, Move> {
    val me = view.viewerId
    val claimed = pending.targets.map { it.playerId }.toSet()
    val twoPlayerAction = pending.targetType == TargetType.SWAP_CARDS ||
        pending.targetType == TargetType.PEEK_THEN_SWAP

    return view.players
        .filterNot { twoPlayerAction && it.id in claimed }
        .flatMap { seat ->
            seat.cards.indices.map { position ->
                CardRef(seat.id, position) to positional(me, seat.id, position)
            }
        }.toMap()
}

private fun positional(me: String, targetId: String, position: Int): Move = Move.Send(
    GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, targetId, position)),
)

// ---------------------------------------------------------------------------- toss-in

private fun tossInTable(view: PlayerView): Table? {
    val toss = view.activeTossIn ?: return null
    if (view.subPhase != GameSubPhase.TOSS_QUEUE_ACTIVE) return null

    val me = view.viewerId
    if (me in toss.playersReadyForNextTurn) {
        return Table(prompt = "Waiting for the others", waiting = true)
    }

    val matching = toss.ranks.joinToString(" or ") { it.serialName }

    // One wrong throw bars you for the rest of the round. Said out loud, because it is a rule
    // a player breaks once and then cannot see they have broken: the window would simply stop
    // accepting cards, with nothing to distinguish "you are barred" from "you were too slow".
    if (me in view.barredFromTossIn) {
        return Table(
            prompt = "The $matching went down",
            detail = "You threw in a wrong card this round, so you cannot toss in again.",
            // Barred from *tossing in*, not from ending your turn. Losing the Vinto call
            // along with it would be a second penalty the rules never mention, and it would
            // land on the player who has just been punished once already.
            choices = listOf(
                Choice(
                    "Continue",
                    Move.Send(GameAction.PlayerTossInFinished(PlayerIdPayload(me))),
                    Tone.PLAY,
                ),
            ) + vintoChoice(view, toss, me),
        )
    }

    val hand = view.players.first { it.id == me }.cards.indices

    // A card is thrown in by touching it, as on the web, rather than by pressing a button and
    // then touching it. It is the one move in the game with no confirmation step and that is
    // deliberate on both sides: a toss-in is a race, and a wrong one costs a penalty card, so
    // the risk that makes it worth confirming is exactly the risk that makes it a bad idea.
    return Table(
        prompt = "The $matching went down — toss in a match?",
        detail = "A wrong one costs you a penalty card.",
        taps = hand.associate { position ->
            val throwIn = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(position)))
            CardRef(me, position) to Move.Send(throwIn)
        },
        choices = buildList {
            val done = GameAction.PlayerTossInFinished(PlayerIdPayload(me))
            add(Choice("Continue", Move.Send(done), Tone.PLAY))

            // Vinto is declared at the *end* of your own turn, which is this window and not
            // the one before you drew. The engine tolerates an early call, but taking it up
            // leaves you still owing the turn you just declared the end of — so the button
            // belongs where the rules put it, and where the web app puts it too.
            val mine = view.players.getOrNull(toss.originalPlayerIndex)?.id == me
            if (mine && view.vintoCallerId == null) {
                val call = GameAction.CallVinto(PlayerIdPayload(me))
                add(Choice("Call Vinto", Move.Send(call), Tone.STAKES))
            }
        },
    )
}

/**
 * Calling Vinto, when this window is the end of your own turn.
 *
 * The rules put the call at the end of a turn, which is this window and not the one before
 * you drew. The engine tolerates an early call, but taking it up leaves you still owing the
 * turn you just declared the end of — so the offer belongs here, which is also where the web
 * app puts it.
 */
private fun vintoChoice(view: PlayerView, toss: ActiveTossIn, me: String): List<Choice> {
    val mine = view.players.getOrNull(toss.originalPlayerIndex)?.id == me
    if (!mine || view.vintoCallerId != null) return emptyList()

    val call = GameAction.CallVinto(PlayerIdPayload(me))
    return listOf(Choice("Call Vinto", Move.Send(call), Tone.STAKES))
}

// ---------------------------------------------------------------------------- endings

/**
 * Somebody called Vinto, and everyone else now plays as one hand.
 *
 * Only the lowest coalition hand counts, so the coalition nominates whoever plays it. The
 * caller sits this out — it is their opponents organising against them.
 */
private fun coalitionTable(view: PlayerView): Table {
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId }?.nickname ?: "Someone"

    return Table(
        prompt = "$caller called Vinto. Who plays for the rest of you?",
        seatTaps = view.players
            .filter { it.id != view.vintoCallerId }
            .associate { seat ->
                seat.id to Move.Send(
                    GameAction.SetCoalitionLeader(LeaderIdPayload(seat.id)),
                )
            },
    )
}

private fun scoringTable(view: PlayerView): Table {
    val mine = view.scores?.get(view.viewerId)
    val best = view.scores?.values?.minOrNull()

    return Table(
        prompt = when {
            mine == null || best == null -> "Round over"
            mine == best -> "Round over — you finished lowest on $mine"
            else -> "Round over — you finished on $mine, best was $best"
        },
        waiting = true,
    )
}

/** A card taken from the discard pile must be played; only a drawn one may be kept. */
private val PendingActionView.canGoToHand: Boolean
    get() = from == PendingCardOrigin.DRAWING
