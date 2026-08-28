package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.getCardValue
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.actorId

/**
 * A place on the table a card can be.
 *
 * Named rather than measured, because where these are on screen is the screen's business and
 * changes with the layout, while *which* of them a card moved between is a fact about the
 * game. Keeping them apart is what lets the movement be worked out here, where it can be
 * tested, and drawn there, where it can be seen (design C5).
 */
sealed interface Anchor {
    data object Deck : Anchor
    data object Discard : Anchor

    /** The card in hand awaiting a decision — it belongs to no seat yet. */
    data object Pending : Anchor

    data class Seat(val playerId: String, val position: Int) : Anchor
}

/**
 * One thing the player should see.
 *
 * Movement is the common case and not the only one: a card turning over, a hand flinching at a
 * penalty and a seat lighting up are all things the game does that a still picture cannot say.
 */
sealed interface Beat {

    /**
     * A card travelling.
     *
     * @param card what to draw in flight, or null to draw a back. A card that arrives
     *   face-down flies face-down: showing a rank in mid-air and hiding it on landing tells
     *   the player something the game did not.
     */
    data class Move(
        val from: Anchor,
        val to: Anchor,
        val card: Card? = null,
        /**
         * A card the table is being *shown* rather than one merely being moved: it travels
         * lit, so the moment reads as an announcement. The web app does the same thing with a
         * green glow, and it is the difference between a card changing places and a player
         * proving something.
         */
        val shown: Boolean = false,
        /**
         * Flown at a dealer's tempo rather than a play's. The opening deal is twenty cards,
         * and twenty cards at the speed a move deserves to be read at is half a minute of
         * watching the deck; a deal is dealt briskly because there is nothing to read on a
         * card arriving face-down.
         */
        val quick: Boolean = false,
    ) : Beat

    /**
     * A card being taken up by an action.
     *
     * **Public.** Everyone sees *which* card was aimed at — that is real information, and it
     * is how you know a bot has just learned its own third card. Only the player entitled to
     * it sees the face, which is what [card] being null means: somebody looked at that card,
     * and you do not get to look with them.
     *
     * Drawn as a lift toward the middle of the table with a glow, rather than a flip in place,
     * so that it reads at a glance across three other hands.
     *
     * This beat is the *onset* only — the card rising, and the table dwelling on it. How long
     * the card stays up is not the beat's to say: an aim is a state, held in the view's
     * `pendingAction.targets` for as long as the action runs, and the stage keeps the card up
     * while [heldUp] still names it. It comes down when a flight takes it or when the state
     * ends. Encoding the lift's whole life in this beat was the bug that made a Queen's first
     * card lower itself while its player was still choosing the second.
     */
    data class Peek(val at: Anchor, val card: Card? = null) : Beat

    /**
     * A card being played, shown off where it lies before it goes anywhere.
     *
     * The web app's "play action": the card swells to two and a half times its size, lit
     * green, and settles. It is the most emphatic thing that happens in a turn, and it is what
     * tells the other three players that this card was *used* rather than merely put down,
     * which changes what they can do next: a discarded action card is still there for the
     * taking.
     *
     * [at] is where the card is lying while it happens, and it is not always the drawn slot: a
     * card taken off the discard, or one thrown in, is played from the pile. Fixing it at the
     * drawn slot made those two teleport out of the pile, swell, and teleport back.
     */
    data class Flourish(val card: Card?, val at: Anchor = Anchor.Pending) : Beat

    /**
     * A card turned face up for the whole table, where it lies.
     *
     * The rules do this twice — a King naming a card, and a throw that missed — and both
     * times the card stays in the hand it was in. Everybody is shown it, and then it goes
     * back to being a card they are expected to remember, which is why it arrives beside the
     * move rather than in the state: it is what happened, not what is.
     */
    data class Reveal(val at: Anchor, val card: Card) : Beat

    /**
     * The discard pile going back into the deck.
     *
     * A real event and currently a silent one in both clients: everything anybody has learned
     * from watching the pile becomes stale at that moment, and the only sign of it is two
     * numbers changing. [cards] is how many went back, which is what makes it read as a
     * sweep rather than as one card moving the wrong way.
     */
    data class Reshuffle(val cards: Int) : Beat

    /**
     * The King's borrowed action.
     *
     * A King declares another rank and performs *that* card's action, which means the next
     * thing the table is asked for belongs to a card nobody played. Naming it is the
     * difference between "why is it asking me to pick two cards" and "ah, it declared a
     * Queen".
     */
    data class Borrowed(val rank: Rank) : Beat

    /**
     * The answer to a declaration: green for right, red for wrong.
     *
     * Declaring a rank is the one move in the game that is a gamble on your own memory, and
     * the outcome is otherwise only visible as a penalty card appearing somewhere.
     */
    data class Verdict(val at: Anchor, val correct: Boolean) : Beat

    /** A hand flinching. Used for the penalty card a wrong guess or a wrong toss-in costs. */
    data class Flinch(val at: Anchor) : Beat

    /** A seat drawing attention to itself, without anything moving. */
    data class Attend(val playerId: String, val kind: Attention) : Beat

    /**
     * Somebody reacting.
     *
     * Bots that only ever move cards are furniture. A line at the right moment — a bot
     * announcing a Vinto call, or wincing at a penalty — is what makes the other three seats
     * feel like opponents, and it costs one string.
     */
    data class Say(val playerId: String, val line: String) : Beat
}

/** Why a seat is being pointed at. */
enum class Attention { TURN, VINTO, PENALTY, COALITION }

/**
 * Beats that happen together.
 *
 * Sequencing is a property of the grouping rather than of each beat (design C2): a scene is
 * the things that happen at once, and scenes happen in order. A swap is one scene of two
 * moves, because the cards cross; a King is three scenes, because it is three things one
 * after another.
 */
typealias Scene = List<Beat>

/**
 * One action, everything it gives the player to see, and the table it leaves behind.
 *
 * The view travels *with* the scenes rather than being published separately, and that is the
 * whole point of the type. Without it a client shows the state after the last move while the
 * overlay is still flying cards from three moves ago — the bots' turns all land at once and
 * then get narrated, which is the one arrangement guaranteed to be unreadable. Carrying the
 * view here lets the screen step to each move as it is shown, so the table and the animation
 * are describing the same moment.
 *
 * Carries the [action] itself as well, because how long the table should dwell on a move
 * depends on what the move *was* — a card being drawn is something to read, aiming a Jack is
 * not — and because a screen teaching the game has to say what just happened rather than only
 * draw it.
 */
data class Frame(
    val action: GameAction,
    val scenes: List<Scene>,
    val view: PlayerView,
) {
    /** Whoever made the move, or null for the engine's own bookkeeping. */
    val actorId: String? get() = action.actorId

    /** Whether this frame takes any time to play. Aiming a Jack moves nothing. */
    val hasSomethingToSee: Boolean get() = scenes.any { it.isNotEmpty() }
}

/**
 * The cards the running action is holding up, and the face each one shows *this* seat.
 *
 * This is the state half of the lift — the beat half is [Beat.Peek], which is only the rise.
 * An aim lasts from its tap until the action resolves, on the game's clock rather than the
 * table's, so its lifetime cannot live in a beat: it lives here, read from the view after
 * every frame. A Queen's two cards therefore stay up *together* until the swap happens or is
 * declined, a King's target hovers through the declaration and its verdict, and a client that
 * dropped a backlog of frames still lifts exactly what the present says is being aimed at.
 *
 * The face rides on the engine's decision and nobody else's, and it is read from the *seat*,
 * not from the target entry — the same lesson [peekScene] carries: a plain peek's face does
 * not travel on the action, the projection turns the card face-up in the hand of whoever is
 * entitled. So the actor of a Queen holds two faces up while everybody else watches two
 * backs, and a Jack's cards are backs on every screen including its player's, because a Jack
 * does not look.
 */
fun PlayerView.heldUp(): Map<Anchor, CardView> =
    pendingAction?.targets.orEmpty().associate { target ->
        val seen = players.firstOrNull { it.id == target.playerId }
            ?.cards?.getOrNull(target.position)
        Anchor.Seat(target.playerId, target.position) to (seen ?: target.card)
    }

/** Everything this action turned face up, as a scene of its own. */
fun revealScene(revealed: List<PublicReveal>): List<Scene> = revealed
    .map { Beat.Reveal(Anchor.Seat(it.playerId, it.position), it.card) }
    .takeIf { it.isNotEmpty() }
    ?.let { listOf(it) }
    .orEmpty()

/**
 * The opening deal, as a dealer deals it: one card to every seat, five times round.
 *
 * The deal is not an action — the dealt table precedes the first `GameAction` — so it cannot
 * be a [Frame]; it is played by the stage before any frame, over the freshly dealt view.
 * Five scenes of four face-down flights: the beats within a wave fly together and the waves
 * follow one another, which reads as dealing rather than as twenty cards materialising —
 * which is what the table used to do, and it is the first thing anybody sees.
 */
fun dealScenes(view: PlayerView): List<Scene> {
    val cards = view.players.maxOfOrNull { it.cards.size } ?: return emptyList()

    return (0 until cards).map { position ->
        view.players.mapNotNull { seat ->
            if (position >= seat.cards.size) return@mapNotNull null
            Beat.Move(Anchor.Deck, Anchor.Seat(seat.id, position), card = null, quick = true)
        }
    }.filter { it.isNotEmpty() }
}

/**
 * The reveal at scoring: every hand turns over, seat by seat, in table order.
 *
 * All four used to flip at once, which spends the round's payoff in a single frame — the
 * moment the player finally sees the three hands they spent the round guessing at. Seat by
 * seat gives each hand its beat. Table order for every viewer, on purpose: an order that
 * favoured the viewer (their own hand last, say) would give two seats different scripts for
 * the same moment, and the visibility matrix holds that two scripts may differ only by a
 * withheld face — never by the shape of what happens.
 *
 * Only cards *newly* face-up turn: what the viewer could already see — their own peeked
 * cards, anything a wrong declaration exposed — is not news and does not flip again. Nothing
 * happens on a view already at scoring (a resumed game), because a reveal that replays on
 * every glance is a reveal that stops meaning anything.
 */
fun scoringScenes(before: PlayerView, after: PlayerView): List<Scene> {
    if (before.phase == GamePhase.SCORING || after.phase != GamePhase.SCORING) return emptyList()

    return after.players.mapNotNull { seat ->
        val previously = before.players.firstOrNull { it.id == seat.id }?.cards.orEmpty()
        seat.cards.mapIndexedNotNull { position, card ->
            val now = card as? CardView.Visible ?: return@mapIndexedNotNull null
            if (previously.getOrNull(position) is CardView.Visible) return@mapIndexedNotNull null
            Beat.Reveal(Anchor.Seat(seat.id, position), now.card)
        }.takeIf { it.isNotEmpty() }
    }
}

/**
 * Frames that happened at one moment, played at one moment.
 *
 * A toss-in window belongs to the whole table at once: it opens, and everybody who holds a
 * match throws it in. The engine still resolves that one player at a time — it has to, since
 * a wrong throw costs a card and the order decides who pays — and each of those resolutions
 * arrives as its own frame, which played one after another turns a scramble into a queue.
 * Four players throwing took four beats of screen time and read as four separate events.
 *
 * Consecutive throws are merged into a single frame here, with their flights in one scene so
 * the cards leave together. Anything else those frames carried — a flinch for a wrong throw,
 * the verdict on it — stays in the scenes behind, in the order it happened, because a penalty
 * is not part of the scramble and should not land in the middle of it.
 *
 * The view kept is the last one, which is the table after all of them have thrown: the same
 * table the flights are landing on.
 */
private fun Frame.isAThrow(): Boolean {
    val first = scenes.firstOrNull() ?: return false
    return first.isNotEmpty() && first.all { it is Beat.Move && it.to == Anchor.Discard }
}

fun List<Frame>.tossedTogether(): List<Frame> {
    val out = mutableListOf<Frame>()
    var group = mutableListOf<Frame>()

    fun flush() {
        when (group.size) {
            0 -> Unit
            1 -> out += group.single()
            else -> {
                val flights = group.mapNotNull { it.scenes.firstOrNull() }.flatten()
                val rest = group.flatMap { it.scenes.drop(1) }
                out += Frame(
                    action = group.last().action,
                    scenes = listOfNotNull(flights.takeIf { it.isNotEmpty() }) + rest,
                    view = group.last().view,
                )
            }
        }
        group = mutableListOf()
    }

    forEach { frame ->
        // A *throw* merges; a failed one does not. A failed throw's first scene is its
        // penalty — the card never left the hand, so there is no flight to merge — and
        // sweeping that into the joint scene puts the penalty, the flinch and the line in the
        // middle of everybody else's throws, which is the one thing the merge is here to
        // prevent.
        if (frame.action is GameAction.ParticipateInTossIn && frame.isAThrow()) {
            group += frame
        } else {
            flush()
            out += frame
        }
    }
    flush()
    return out
}

/**
 * What the player should see, given what happened.
 *
 * Takes two **views** rather than two states, and that is the whole design (C1). A client
 * never holds `GameState` — redaction is the point — so animation derived from state works in
 * a solo game and cannot be carried to a room, which is the mode it is for. Derived from the
 * view it works in both, and it gains a property worth as much: it *cannot* animate a card the
 * player is not entitled to see, because it was never given one.
 *
 * Takes the action rather than diffing the two views, because a diff cannot tell which card
 * went where when two move at once. A swap moves one in and another out; animating them in the
 * wrong order is worse than not animating them.
 *
 * Returns nothing for the many actions that move nothing — aiming a Jack, agreeing that a
 * toss-in window is over. Silence is the right answer there, not a guess.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
fun choreograph(action: GameAction, before: PlayerView, after: PlayerView): List<Scene> {
    val penalty = penaltyScene(action, before, after)

    val main: Scene = when (action) {
        // The deck to the space in front of you, face up — the rules have the drawn card
        // revealed publicly, and it is the single most informative moment of somebody else's
        // turn. Somebody else's card still turns over on the way, which is what marks it as
        // theirs rather than yours.
        is GameAction.DrawCard ->
            listOf(
                Beat.Move(
                    from = Anchor.Deck,
                    to = Anchor.Pending,
                    card = after.pendingCard(),
                ),
            )

        is GameAction.DiscardCard ->
            listOf(Beat.Move(Anchor.Pending, Anchor.Discard, after.discardTop))

        // Two cards at once, and the order is the point: the new one goes in, the old one
        // comes out and lands face-up where everybody can read it.
        // Two cards at once: the drawn one into the hand, the old one onto the pile.
        //
        // Onto the pile in *every* case, which is worth stating because the engine reaches it
        // by two different routes. Say nothing or guess wrong and the card is discarded
        // outright. Guess right and it becomes the pending action instead, its own action
        // about to be played — but a card in play lies on the discard, so that is where it
        // goes and where it stays. A guess that came off travels lit, because the table has to
        // see that the call was good.
        is GameAction.SwapCard -> {
            val seat = Anchor.Seat(action.payload.playerId, action.payload.position)
            val called = after.pendingCard()?.takeIf { action.payload.declaredRank != null }
            listOfNotNull(
                Beat.Move(Anchor.Pending, seat, before.pendingCard()),
                (called ?: after.discardTop)?.let {
                    Beat.Move(seat, Anchor.Discard, it, shown = called != null)
                },
            )
        }

        // Aiming an action. A peek is shown to the whole table; an Ace names a victim rather
        // than a card, so there is nothing to lift and somebody to point at instead.
        is GameAction.SelectActionTarget -> when (val payload = action.payload) {
            is SelectActionTargetPayload.Ace -> listOf(
                Beat.Attend(payload.targetPlayerId, Attention.PENALTY),
                Beat.Say(payload.targetPlayerId, "Drawing…"),
            )

            is SelectActionTargetPayload.Positional -> peekScene(after, payload)
        }

        // A played action ends with its card on the pile — but for a King or a Queen that is
        // several moves later, so it is driven by the pile growing rather than by the action.
        // Held up in the middle before it goes anywhere, so the table can read it.
        // Playing a card puts it on the pile, so it *goes* there, lit and swelling on the
        // way — the web app's "play action", and the loudest movement in a turn. It used to
        // be lifted in place and set down again with the pile quietly filling in behind it,
        // which is a card teleporting with a flourish in front of it.
        // Playing a card is two beats, in this order: it is shown off where it lies, and then
        // it goes to the pile. The flourish is the web app's, and the journey is ours — the
        // engine opens the toss-in window the moment the action starts, so the card has to be
        // lying on the pile the window is open for.
        is GameAction.UseCardAction ->
            listOfNotNull((before.pendingCard() ?: after.pendingCard())?.let(Beat::Flourish))

        // Taking the top of the discard to play it moves nothing — the card is on the pile,
        // and a card whose action is being played is drawn on the pile — but it is still a
        // card being *played*, so it is shown off where it lies like any other.
        is GameAction.PlayDiscard ->
            listOfNotNull(after.pendingCard()?.let { Beat.Flourish(it, Anchor.Discard) })

        // The moment a thrown card's action begins. The card is already on the pile — it flew
        // there when it was thrown — so nothing moves, but it is a card being *played* and the
        // table says so, exactly as it does for one played from the hand.
        is GameAction.PlayerTossInFinished ->
            listOfNotNull(
                after.pendingCard()
                    ?.takeIf { before.pendingAction == null && after.pendingAction != null }
                    ?.let { Beat.Flourish(it, Anchor.Discard) },
            )

        is GameAction.ConfirmPeek,
        is GameAction.SkipPeek,
        -> discardScene()

        // Declining a swap you had lined up. The two cards you were holding over stay where
        // they are, so nothing travels — but "I looked and decided not to" is a decision the
        // rest of the table should see, and it showed nothing at all. The web app shakes the
        // pair; a flinch is this table's word for the same thing.
        is GameAction.SkipJackSwap, is GameAction.SkipQueenSwap ->
            before.pendingAction?.targets.orEmpty().map {
                Beat.Flinch(Anchor.Seat(it.playerId, it.position))
            }

        // The King says what it is pretending to be, and then does that.
        // A King names a card in somebody's hand and, if the name was right, that card comes
        // out of the hand: to the pile, or into play from the pile if it has an action of its
        // own. Either way it *leaves a hand*, and it did so without moving — the one card in
        // the game that could still teleport, because the case that watches for teleports
        // never played a King.
        //
        // A wrong name leaves the card where it is, revealed, and costs a card: nothing moves
        // but the penalty, which the penalty scene animates.
        is GameAction.DeclareKingAction ->
            listOf(Beat.Borrowed(action.payload.declaredRank)) + namedCardScene(before, after)

        // The two swaps that happen inside an action rather than as a move of their own, so
        // their endpoints come from the targets the action was aimed at.
        is GameAction.ExecuteJackSwap, is GameAction.ExecuteQueenSwap -> crossScene(before)

        is GameAction.ParticipateInTossIn -> tossScene(action, before, after)

        is GameAction.CallVinto -> listOf(
            Beat.Attend(action.payload.playerId, Attention.VINTO),
            Beat.Say(action.payload.playerId, "Vinto!"),
        )

        is GameAction.SetCoalitionLeader ->
            listOf(Beat.Attend(action.payload.leaderId, Attention.COALITION))

        else -> emptyList()
    }

    val verdict = verdictScene(action, penalty != null)
    val table = tableScene(before, after)

    val played = (before.pendingCard() ?: after.pendingCard())
        ?.takeIf { action is GameAction.UseCardAction }
        ?.let { listOf(Beat.Move(Anchor.Pending, Anchor.Discard, it, shown = true)) }

    // The reveal rides the round-ending frame as trailing scenes, so the hands turn over
    // seat by seat after whatever the last move showed — see [scoringScenes].
    return listOfNotNull(main.takeIf { it.isNotEmpty() }, played, verdict, penalty, table) +
        scoringScenes(before, after)
}

/**
 * What happened to the table itself, rather than to anybody's cards.
 *
 * Read from the two views rather than from the action, because neither is anybody's *move*:
 * the deck refills and the turn passes inside the engine's own bookkeeping, at the end of
 * whatever action happened to finish a turn. There is no action to attach them to, which is
 * exactly why both went unnoticed in the web app as well.
 */
private fun tableScene(before: PlayerView, after: PlayerView): Scene? {
    val beats = mutableListOf<Beat>()

    // The pile going back into the deck. The draw pile only ever grows this way.
    val refilled = after.drawPileSize - before.drawPileSize
    if (refilled > 0) beats += Beat.Reshuffle(refilled)

    // The turn moving. Three bots take theirs in under a second between one tap and the
    // next, and a ring that is simply *on* the active seat is easy to lose track of; a flash
    // as it arrives is what makes the hand-off followable.
    val was = before.players.getOrNull(before.currentPlayerIndex)?.id
    val now = after.players.getOrNull(after.currentPlayerIndex)?.id
    if (now != null && now != was && after.phase != GamePhase.SCORING) {
        beats += Beat.Attend(now, Attention.TURN)
    }

    return beats.takeIf { it.isNotEmpty() }
}

/**
 * Whether a declaration was right, shown on the card it was about.
 *
 * Derived from whether a penalty followed rather than from comparing ranks: a wrong call costs
 * a card and that is the only thing the rules do about it, so the penalty *is* the verdict.
 * One check covers the swap declaration and the King, and would cover a third if one were
 * added.
 */
private fun verdictScene(action: GameAction, penalised: Boolean): Scene? = when (action) {
    is GameAction.SwapCard -> action.payload.declaredRank?.let {
        listOf(Beat.Verdict(Anchor.Discard, correct = !penalised))
    }

    is GameAction.DeclareKingAction ->
        listOf(Beat.Verdict(Anchor.Discard, correct = !penalised))

    else -> null
}

/**
 * The card a penalty adds to a hand.
 *
 * Not attached to any one action, because several can cause one: a wrong declaration, a wrong
 * toss-in, an Ace aimed at you. What they have in common is a hand that grew, which is
 * exactly what is checked — and it means a penalty introduced later animates without anybody
 * remembering to teach this function about it.
 */
private fun penaltyScene(action: GameAction, before: PlayerView, after: PlayerView): Scene? {
    val grew = after.players.mapNotNull { seat ->
        val was = before.players.firstOrNull { it.id == seat.id }?.cards?.size ?: return@mapNotNull null
        if (seat.cards.size > was) seat to was else null
    }
    if (grew.isEmpty()) return null

    return grew.flatMap { (seat, was) ->
        val landing = Anchor.Seat(seat.id, was)
        listOf(
            Beat.Move(Anchor.Deck, landing),
            Beat.Flinch(landing),
            Beat.Attend(seat.id, Attention.PENALTY),
        )
    } + reactionTo(action, grew.first().first.id)
}

/** A line for the seat that just took a penalty, if the action explains why. */
private fun reactionTo(action: GameAction, playerId: String): List<Beat> = when (action) {
    is GameAction.SwapCard -> if (action.payload.declaredRank != null) {
        listOf(Beat.Say(playerId, "Wrong call."))
    } else {
        emptyList()
    }

    is GameAction.ParticipateInTossIn -> listOf(Beat.Say(playerId, "Wrong rank."))
    else -> emptyList()
}

/**
 * The card an action was just aimed at, lifted for everyone to see.
 *
 * Aimed at the *payload's* target rather than the pending action's last entry, because the
 * pending action is projected per seat and a watcher is not always given the list — but the
 * action itself says what was aimed at, and that a card was looked at is public.
 */
private fun peekScene(after: PlayerView, payload: SelectActionTargetPayload.Positional): Scene {
    val at = Anchor.Seat(payload.targetPlayerId, payload.position)

    // The face comes from the seat, not from the pending action. A peek does not travel on the
    // action — the engine records that the looker now *knows* the card, and the projection
    // turns it face-up for whoever is entitled. Reading the target entry instead returns null
    // even for your own peek, which is how this went out showing you nothing the first time.
    val seen = after.players
        .firstOrNull { it.id == payload.targetPlayerId }
        ?.cards?.getOrNull(payload.position) as? CardView.Visible

    return listOf(Beat.Peek(at, seen?.card))
}

/**
 * The end of an action, which moves nothing.
 *
 * A card whose action is being played is *on* the pile for the whole of it — that is why a
 * toss-in window is open for its rank — so when the engine finally records the discard there
 * is nothing left to animate. This used to fly the card from the pending slot to the pile,
 * which was a card appearing beside the hand it had already left and flying to a place it was
 * already lying in.
 */
private fun discardScene(): Scene = emptyList()

/**
 * The card a King named, on its way out of the hand it was named in.
 *
 * Only when the name was right, which is read from the hand rather than from the rank: a
 * correct name takes the card out — to the pile, or into play from the pile if it has its own
 * action — and a wrong one leaves it where it is. Either way the card is public by then, so it
 * travels face up and lit: this is the moment the King's guess is answered.
 */
private fun namedCardScene(before: PlayerView, after: PlayerView): Scene {
    val target = before.pendingAction?.targets?.firstOrNull() ?: return emptyList()
    val had = before.players.firstOrNull { it.id == target.playerId }?.cards?.size
    val has = after.players.firstOrNull { it.id == target.playerId }?.cards?.size
    if (had == null || has == null || has >= had) return emptyList()

    val named = after.pendingCard() ?: after.discardTop
    return listOf(
        Beat.Move(
            Anchor.Seat(target.playerId, target.position),
            Anchor.Discard,
            named,
            shown = true,
        ),
    )
}

/** A Jack or a Queen exchanging two cards, which cross. */
private fun crossScene(before: PlayerView): Scene {
    val targets = before.pendingAction?.targets.orEmpty()
    if (targets.size < TWO) return emptyList()

    val (first, second) = targets
    val firstSeat = Anchor.Seat(first.playerId, first.position)
    val secondSeat = Anchor.Seat(second.playerId, second.position)
    val seen = before.pendingAction?.playerId == before.viewerId

    return listOf(
        Beat.Move(firstSeat, secondSeat, (first.card as? CardView.Visible)?.card.takeIf { seen }),
        Beat.Move(secondSeat, firstSeat, (second.card as? CardView.Visible)?.card.takeIf { seen }),
    )
}

private fun tossScene(
    action: GameAction.ParticipateInTossIn,
    before: PlayerView,
    after: PlayerView,
): Scene {
    val who = action.payload.playerId
    val had = before.players.firstOrNull { it.id == who }?.cards?.size ?: return emptyList()
    val has = after.players.firstOrNull { it.id == who }?.cards?.size ?: return emptyList()
    val thrown = had - has
    if (thrown <= 0) return emptyList()

    // Counted from the *hand*, not from the pile.
    //
    // A card thrown in leaves its hand at once and reaches the discard later — the engine
    // queues the action and writes the card to the pile when it resolves — so a scene keyed
    // on the pile growing produced nothing at all for the throw itself, and the card was gone
    // from a hand and not yet anywhere else. Which is what a player sees as "there was no
    // animation for the toss-in".
    //
    // What it looks like on the table is what a person does: the card goes onto the pile as
    // it is thrown, face up, because a toss-in is a public claim about a rank.
    val face = after.discardTop?.takeIf { after.discardCount > before.discardCount }
        ?: after.activeTossIn?.queuedActions?.lastOrNull()?.let { queued ->
            Card(
                id = "thrown-${queued.playerId}-${queued.rank.serialName}",
                rank = queued.rank,
                value = getCardValue(queued.rank),
                played = false,
            )
        }

    return action.payload.positions.take(thrown).map { position ->
        Beat.Move(Anchor.Seat(who, position), Anchor.Discard, face)
    }
}

private fun PlayerView.pendingCard(): Card? = (pendingAction?.card as? CardView.Visible)?.card

private const val TWO = 2

/** Every rank the choreography might need to draw, for a client that preloads its art. */
internal val EVERY_RANK: List<Rank> = Rank.entries
