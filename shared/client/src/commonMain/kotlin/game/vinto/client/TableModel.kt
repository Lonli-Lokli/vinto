package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.DeclareCardsPayload
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
import game.vinto.shapes.hasAction

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
    val prompt: Ask,
    /**
     * The smaller line under it: the rule that applies, or what the card in play does.
     *
     * Separate from [prompt] because they are read differently — the prompt is what you are
     * being asked, and this is what you need to know to answer it. Running them together is
     * how a heading turns into a paragraph nobody reads.
     */
    val detail: Detail? = null,
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
    val help: Explains? = null,
    /** True when the player has nothing to do but watch. */
    val waiting: Boolean = false,
    /** The cards the screen may show face-up. See [revealedTo]. */
    val revealed: Set<CardRef> = emptySet(),
    /**
     * Declared ranks worn by cards, for every seat to read: what a coalition member has
     * *claimed* a card to be. A label on the card back, never the card itself — a claim is
     * only as good as the memory it came from.
     */
    val badges: Map<CardRef, String> = emptyMap(),
)

/** A card on the table: whose, and which slot. */
data class CardRef(val playerId: String, val position: Int)

/** A button. */
data class Choice(val label: Label, val move: Move, val tone: Tone = Tone.NEUTRAL)

/** A rank the player may name. */
data class RankChoice(val rank: Rank, val move: Move, val muted: Boolean = false)

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

    /** Final round: which rank do I *claim* my card at this position is? */
    data class DeclareRank(val position: Int) : Question
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
 * web app does (`canSeePlayerCard` in `legacy-web/apps/vinto/src/app/components/logic`). The exceptions
 * are the two moments where the rules themselves turn cards over: setup, when you are told to
 * look at two of your own, and scoring, when every hand goes face-up. The coalition leader
 * used to be a third and is no longer: coalition knowledge travels as *declared* claims
 * (`DECLARE_CARDS`, worn as [Table.badges]), never as real cards.
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
    return view.pendingAction
        ?.takeIf { it.playerId == view.viewerId }
        ?.targets
        .orEmpty()
        .mapTo(mutableSetOf()) { CardRef(it.playerId, it.position) }
}

@Suppress("ReturnCount")
fun tableFor(view: PlayerView, question: Question = Question.None): Table {
    val me = view.players.firstOrNull { it.id == view.viewerId }
        ?: return Table(prompt = Ask.Watching, waiting = true)

    if (view.phase == GamePhase.SCORING) return scoringTable(view).showing(view)
    if (view.phase == GamePhase.SETUP) {
        return setupTable(view, me.id, me.knownCardPositions).showing(view)
    }

    // The coalition has to choose who plays its hand before the final round can run.
    val coalitionUndecided = view.vintoCallerId != null && view.coalitionLeaderId == null
    if (coalitionUndecided && view.viewerId != view.vintoCallerId) {
        return coalitionTable(view).showing(view)
    }

    // The player tapped one of their own cards to declare it: the rank picker.
    if (question is Question.DeclareRank && mayDeclare(view)) {
        return declareOwnCardTable(view, question.position).showing(view)
    }

    tossInTable(view)?.let { return it.showing(view) }

    val pending = view.pendingAction
    if (pending != null && pending.playerId == view.viewerId) {
        return pendingTable(view, pending, question).showing(view)
    }

    val current = view.players.getOrNull(view.currentPlayerIndex)
    if (current?.id != view.viewerId || pending != null) {
        val watching = Table(prompt = Ask.SomebodyIsPlaying(playing(current, view)), waiting = true)
        // A coalition member waiting through the final round can still talk: tapping one of
        // their own cards opens the claim picker.
        return if (mayDeclare(view)) {
            watching.copy(
                detail = Detail.TapACardToSayWhatItIs,
                taps = me.cards.indices.associate { position ->
                    CardRef(me.id, position) to Move.Ask(Question.DeclareRank(position))
                },
            ).showing(view)
        } else {
            watching.showing(view)
        }
    }

    return turnStartTable(view).showing(view)
}

/** Table talk is for coalition members, during the final round, once a leader is chosen. */
private fun mayDeclare(view: PlayerView): Boolean =
    view.phase == GamePhase.FINAL &&
        view.vintoCallerId != null &&
        view.viewerId != view.vintoCallerId &&
        view.coalitionLeaderId != null

private fun declareOwnCardTable(view: PlayerView, position: Int): Table = Table(
    prompt = Ask.WhatDoYouSayThisCardIs,
    detail = Detail.TableTalkIsTakenOnTrust,
    choices = listOf(Choice(Label.Back, Move.Ask(Question.None))),
    ranks = ALL_RANKS.map { rank ->
        RankChoice(
            rank,
            Move.Send(
                GameAction.DeclareCards(DeclareCardsPayload(view.viewerId, mapOf(position to rank))),
            ),
        )
    },
)

/** Every standing claim, worn on the claimed card for the whole table to read. */
private fun declaredBadges(view: PlayerView): Map<CardRef, String> =
    view.players
        .flatMap { seat ->
            seat.declaredCards.map { (position, rank) -> CardRef(seat.id, position) to rank.serialName }
        }
        .toMap()

private fun Table.showing(view: PlayerView) =
    copy(revealed = revealedTo(view), help = helpFor(view), badges = declaredBadges(view))

/**
 * What the "?" explains, for whatever is happening.
 *
 * The card in play if there is one, since that is nearly always what the player is unsure
 * about, and otherwise the rule that governs the phase.
 */
private fun helpFor(view: PlayerView): Explains {
    val pending = (view.pendingAction?.card as? CardView.Visible)?.card
    if (pending != null && view.pendingAction?.playerId == view.viewerId) {
        return Explains.TheCardInPlay(pending.rank)
    }

    return when {
        view.phase == GamePhase.SETUP -> Explains.HowSetupWorks
        view.phase == GamePhase.SCORING -> Explains.HowScoringWorks
        view.activeTossIn != null -> Explains.HowTossingInWorks
        view.vintoCallerId != null -> Explains.HowTheFinalRoundWorks
        else -> Explains.HowATurnWorks
    }
}

// ---------------------------------------------------------------------------- setup

private fun setupTable(view: PlayerView, myId: String, peeked: List<Int>): Table {
    val hand = view.players.first { it.id == myId }.cards.indices

    if (peeked.size < SETUP_PEEKS) {
        val left = SETUP_PEEKS - peeked.size
        return Table(
            prompt = if (left == SETUP_PEEKS) Ask.LookAtTwoOfYours else Ask.OneMoreToLookAt,
            taps = hand.filterNot { it in peeked }.associate { position ->
                val peek = GameAction.PeekSetupCard(PositionPayload(myId, position))
                CardRef(myId, position) to Move.Send(peek)
            },
        )
    }

    // Everyone peeks before anyone plays, so this waits on the rest of the table — which for
    // a solo game is nobody, since the bots are dealt theirs.
    return Table(
        prompt = Ask.ReadyWhenYouAre,
        choices = listOf(
            Choice(
                Label.StartRound,
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
        choices += Choice(Label.DrawCard, Move.Send(GameAction.DrawCard(PlayerIdPayload(me))), Tone.PLAY)
    }

    // Only an action card nobody has played yet can be taken, and taking it commits you to
    // playing it — it cannot go into your hand.
    if (top != null && top.actionText != null && !top.played) {
        choices += Choice(
            Label.UseFromPile(top.rank),
            Move.Send(GameAction.PlayDiscard(PlayerIdPayload(me))),
            Tone.PLAY,
        )
    }

    return Table(prompt = Ask.YourTurn, choices = choices)
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
            Label.UseAction,
            Move.Send(GameAction.UseCardAction(PlayerIdPayload(me))),
            Tone.PLAY,
        )
    }

    // A card taken off the discard pile must be played; it cannot be kept.
    if (pending.canGoToHand) {
        choices += Choice(Label.SwapCards, Move.Ask(Question.WhichSlot), Tone.KEEP)
        choices += Choice(Label.Discard, Move.Send(GameAction.DiscardCard(PlayerIdPayload(me))))
    }

    val what = Ask.YouDrew(card?.rank)
    val does = card?.rank?.takeIf { getCardConfig(it).action != null }?.let(Detail::WhatTheCardDoes)
    return Table(prompt = what, detail = does, choices = choices)
}

private fun whichSlotTable(view: PlayerView): Table {
    val me = view.viewerId
    val hand = view.players.first { it.id == me }.cards.indices

    return Table(
        prompt = Ask.WhichCardDoesItReplace,
        choices = listOf(Choice(Label.Back, Move.Ask(Question.None))),
        taps = hand.associate { position ->
            CardRef(me, position) to Move.Ask(Question.CallRank(position))
        },
    )
}

private fun callRankTable(view: PlayerView, position: Int): Table {
    val me = view.viewerId

    return Table(
        prompt = Ask.NameWhatYouArePuttingDown,
        detail = Detail.RightPlaysItWrongCostsACard,
        choices = listOf(
            Choice(
                Label.JustSwap,
                Move.Send(GameAction.SwapCard(SwapCardPayload(me, position))),
                Tone.KEEP,
            ),
            Choice(Label.Back, Move.Ask(Question.WhichSlot)),
        ),
        // Only the action ranks. Declaring a 2-6 or the Joker is legal but pointless -
        // a right guess wins nothing, since the card has no action to play, while a wrong
        // one still costs a penalty card. A button that can only lose is not a choice.
        ranks = ALL_RANKS.filter(::hasAction).map { rank ->
            RankChoice(rank, Move.Send(GameAction.SwapCard(SwapCardPayload(me, position, rank))))
        },
    )
}

// ---------------------------------------------------------------------------- aiming an action

private fun targetingTable(view: PlayerView, pending: PendingActionView): Table {
    val card = (pending.card as? CardView.Visible)?.card
    // `KingDeclared`, not a sentence built from `getCardShortDescription` — that field is
    // `Card.actionText`, which is inside the canonical hash and cannot be translated
    // (`CardCopyIsDataTest`). The renderer reaches for `longDescription` instead.
    val borrowed = pending.declaredRank?.let(Detail::KingDeclared)

    return withBorrowed(borrowed) {
        when (pending.targetType) {
            TargetType.OWN_CARD -> peekTable(view, pending, Ask.LookAtOneOfYourOwn, ownTaps(view))
            TargetType.OPPONENT_CARD ->
                peekTable(view, pending, Ask.LookAtOneOfAnotherPlayers, opponentTaps(view))

            // A Jack swaps blind; a Queen looks first. Same two-target shape, different question
            // at the end, and neither may be skipped until both cards have been named.
            TargetType.SWAP_CARDS -> twoCardTable(
                view = view,
                pending = pending,
                prompt = Ask.ChooseTwoFromDifferentPlayers,
                swap = GameAction.ExecuteJackSwap(PlayerIdPayload(view.viewerId)),
                leave = GameAction.SkipJackSwap(PlayerIdPayload(view.viewerId)),
            )

            TargetType.PEEK_THEN_SWAP -> twoCardTable(
                view = view,
                pending = pending,
                prompt = Ask.LookAtTwoFromDifferentPlayers,
                swap = GameAction.ExecuteQueenSwap(PlayerIdPayload(view.viewerId)),
                leave = GameAction.SkipQueenSwap(PlayerIdPayload(view.viewerId)),
            )

            TargetType.DECLARE_ACTION -> declareTable(view, pending)
            TargetType.FORCE_DRAW -> forceDrawTable(view)

            null -> Table(
                prompt = Ask.TheCardIsWaiting(card?.rank),
                choices = listOf(giveUp(view.viewerId)),
            )
        }
    }
}

/**
 * Adds the King's borrowed action to whatever the table is asking.
 *
 * A King performs another rank's action, so the next question belongs to a card nobody
 * played. Without naming it, "choose two cards from two different players" arrives with no
 * explanation — the Queen it is imitating was never on the table.
 */
private inline fun withBorrowed(borrowed: Detail?, build: () -> Table): Table {
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
private fun giveUp(me: String) =
    Choice(Label.PutItDown, Move.Send(GameAction.ConfirmPeek(PlayerIdPayload(me))))

private fun peekTable(
    view: PlayerView,
    pending: PendingActionView,
    prompt: Ask,
    taps: Map<CardRef, Move>,
): Table = if (pending.targets.isEmpty()) {
    Table(prompt = prompt, choices = listOf(giveUp(view.viewerId)), taps = taps)
} else {
    Table(
        prompt = Ask.RememberIt,
        choices = listOf(
            Choice(
                Label.Done,
                Move.Send(GameAction.ConfirmPeek(PlayerIdPayload(view.viewerId))),
                Tone.PLAY,
            ),
        ),
    )
}

private fun twoCardTable(
    view: PlayerView,
    pending: PendingActionView,
    prompt: Ask,
    swap: GameAction,
    leave: GameAction,
): Table = if (pending.targets.size < TWO_TARGETS) {
    Table(prompt = prompt, choices = listOf(giveUp(view.viewerId)), taps = anyTaps(view, pending))
} else {
    Table(
        prompt = Ask.SwapThem,
        choices = listOf(
            Choice(Label.SwapCards, Move.Send(swap), Tone.PLAY),
            Choice(Label.LeaveThem, Move.Send(leave)),
        ),
    )
}

private fun declareTable(view: PlayerView, pending: PendingActionView): Table =
    if (pending.targets.isEmpty()) {
        Table(
            prompt = Ask.ChooseAnyCard,
            choices = listOf(giveUp(view.viewerId)),
            taps = anyTaps(view, pending),
        )
    } else {
        Table(
            prompt = Ask.SayWhatItIsAndPlayIt,
            choices = listOf(giveUp(view.viewerId)),
            // The King may name any rank - taking an opponent's 2 out of their hand is a
            // real play - but naming an action rank is the common case, so those lead and
            // the actionless ranks follow, muted rather than hidden.
            ranks = ALL_RANKS.sortedBy { !hasAction(it) }.map { rank ->
                RankChoice(
                    rank,
                    Move.Send(
                        GameAction.DeclareKingAction(DeclareKingActionPayload(view.viewerId, rank)),
                    ),
                    muted = !hasAction(rank),
                )
            },
        )
    }

/** The only action that names a player rather than a card. */
private fun forceDrawTable(view: PlayerView): Table = Table(
    prompt = Ask.WhoDrawsACard,
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

/**
 * Whose turn it is, as a [Speaker] rather than a name — because the answer conjugates.
 *
 * The seat being waited on can be the viewer's own: a pending action belonging to somebody
 * else reaches the watching branch on your own turn. It used to be `Speaker.Named(nickname)`
 * unconditionally, and `initializeGame` calls seat zero "You", so the table read
 * **"You is playing"**. The engine's nickname cannot be corrected instead: `PlayerState` is
 * in `GameState`, which is inside the canonical hash the frozen corpus pins.
 */
private fun playing(current: PlayerSeatView?, view: PlayerView): Speaker = when {
    current == null -> Speaker.Nobody
    current.id == view.viewerId -> Speaker.You
    else -> Speaker.Named(current.nickname)
}

private fun tossInTable(view: PlayerView): Table? {
    val toss = view.activeTossIn ?: return null
    if (view.subPhase != GameSubPhase.TOSS_QUEUE_ACTIVE) return null

    val me = view.viewerId
    if (me in toss.playersReadyForNextTurn) {
        return Table(prompt = Ask.WaitingForTheOthers, waiting = true)
    }

    // One wrong throw bars you from this card — and, in the final round, from the rest of it.
    // Said out loud, because it is a rule a player breaks once and then cannot see they have
    // broken: the window would simply stop accepting cards, with nothing to distinguish "you
    // are barred" from "you were too slow". Which of the two it is matters to the player as
    // much as the bar does: one is worth waiting out and the other is the round over for them.
    if (me in view.barredFromTossIn) {
        return Table(
            prompt = Ask.TossIn(toss.ranks, barred = true),
            detail = if (view.phase == GamePhase.FINAL) {
                Detail.BarredForTheRestOfTheRound
            } else {
                Detail.BarredFromThisCard
            },
            // Barred from *tossing in*, not from ending your turn. Losing the Vinto call
            // along with it would be a second penalty the rules never mention, and it would
            // land on the player who has just been punished once already.
            choices = listOf(
                Choice(
                    Label.Continue,
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
        prompt = Ask.TossIn(toss.ranks, barred = false),
        detail = Detail.AWrongOneCostsAPenaltyCard,
        taps = hand.associate { position ->
            val throwIn = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(position)))
            CardRef(me, position) to Move.Send(throwIn)
        },
        choices = buildList {
            val done = GameAction.PlayerTossInFinished(PlayerIdPayload(me))
            add(Choice(Label.Continue, Move.Send(done), Tone.PLAY))

            // Vinto is declared at the *end* of your own turn, which is this window and not
            // the one before you drew. The engine tolerates an early call, but taking it up
            // leaves you still owing the turn you just declared the end of — so the button
            // belongs where the rules put it, and where the web app puts it too.
            val mine = view.players.getOrNull(toss.originalPlayerIndex)?.id == me
            if (mine && view.vintoCallerId == null) {
                val call = GameAction.CallVinto(PlayerIdPayload(me))
                add(Choice(Label.CallVinto, Move.Send(call), Tone.STAKES))
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
    return listOf(Choice(Label.CallVinto, Move.Send(call), Tone.STAKES))
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
        prompt = Ask.WhoPlaysForYou(Speaker.Named(caller)),
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
        prompt = Ask.RoundOver(yours = mine, best = best),
        detail = when (roundEndReason(view)) {
            RoundEndReason.VINTO_CALLED -> {
                val caller = view.players.firstOrNull { it.id == view.vintoCallerId }?.nickname
                Detail.ScoredAgainstTheCaller(Speaker.Named(caller ?: "Someone"))
            }

            RoundEndReason.DECK_EXHAUSTED -> Detail.TheDeckRanOut
            null -> null
        },
        waiting = true,
    )
}

/**
 * How many turns of the final round are still to be played, counting the one in progress.
 *
 * The rules give every non-caller exactly one more turn, and the round ends when play comes
 * back to the caller — so the answer is the number of seats between the current player
 * (inclusive: their turn is being played, not spent) and the caller, walking in turn order.
 * `null` when there is nothing to say: outside the final round, and in the closing frames
 * where play has already returned to the caller — the reveal itself is the message there.
 *
 * This exists because three of those turns can pass in under a second when the coalition is
 * all bots, and a player who looked away for one of them has no way to know how close the
 * reveal is. A count is the whole answer.
 */
fun finalRoundTurnsLeft(view: PlayerView): Int? {
    if (view.phase != GamePhase.FINAL) return null
    val caller = view.players.indexOfFirst { it.id == view.vintoCallerId }
    if (caller < 0) return null

    val seats = view.players.size

    // Where to count from. During a toss-in window the turn that opened it is already
    // spent, so the count starts after the window's owner — which also disambiguates the
    // two moments the current player alone cannot: at the call the open window is still
    // the *caller's* (a full lap remains), and at the end the engine has already advanced
    // past the caller to a seat that will never play (nothing remains).
    val from = view.activeTossIn?.originalPlayerIndex?.let { (it + 1) % seats }
        ?: view.currentPlayerIndex

    var index = from
    var left = 0
    while (index != caller && left < seats) {
        left++
        index = (index + 1) % seats
    }

    // Zero means play has come back round to the caller: the hands are about to go over,
    // and a count of nothing is not worth saying — the reveal itself is the message.
    return left.takeIf { it > 0 }
}

/**
 * Why the hands went face-up.
 *
 * Two things end a round and they mean opposite advice: a Vinto call is somebody's judgement
 * being tested, and an exhausted deck is the clock running out on everybody at once. The
 * scoring screen used to show the totals without saying which had happened — and a player
 * who never called and never saw a call has every right to ask why the round is over.
 *
 * Derivable rather than recorded: a round that reaches scoring with no caller can only have
 * ended on the deck, because a call is the one other way out of `PLAYING`.
 */
enum class RoundEndReason { VINTO_CALLED, DECK_EXHAUSTED }

fun roundEndReason(view: PlayerView): RoundEndReason? = when {
    view.phase != GamePhase.SCORING -> null
    view.vintoCallerId != null -> RoundEndReason.VINTO_CALLED
    else -> RoundEndReason.DECK_EXHAUSTED
}

/** A card taken from the discard pile must be played; only a drawn one may be kept. */
private val PendingActionView.canGoToHand: Boolean
    get() = from == PendingCardOrigin.DRAWING

/**
 * Whether a line of the move log is only repeating what the panel is already asking.
 *
 * The panel's prompt and the log are built from the same events, so the newest line was
 * routinely the sentence directly above it — "You drew the 5", twice, six pixels apart.
 *
 * This used to be a comparison of two rendered strings, which worked by coincidence: an [Ask]
 * and a [Say] are different types that happened to produce the same words. Saying it as a
 * rule instead makes the relationship explicit and survives translation — two sentences that
 * merely *look* alike in English are no longer silently deduplicated in a language where they
 * do not.
 */
fun Ask.echoedBy(line: Say): Boolean = when {
    this is Ask.YouDrew && line is Say.DrewKnown -> line.who is Speaker.You && line.rank == rank
    else -> false
}
