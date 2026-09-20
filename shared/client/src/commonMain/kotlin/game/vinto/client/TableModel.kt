package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PendingTargetView
import game.vinto.engine.PlayerSeatView
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.tossInIsOpen
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Believed
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.DeclareCardsPayload
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.TableTalk
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
    /**
     * Whole seats on offer, for the two moves that name a person rather than a card: an Ace,
     * which makes somebody draw, and the coalition choosing who plays its hand.
     *
     * A list rather than a map keyed by id, because these are drawn twice and one of the two
     * needs them in order and by name. The felt makes each seat's plate tappable, which is the
     * better gesture; the rail draws them as buttons, which is the part that says the choice
     * exists at all. Every other question the table asks is answered on the rail, so a
     * question answerable only by tapping the felt read as a question with no answer.
     */
    val seats: List<SeatChoice> = emptyList(),
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
     * only as good as the memory it came from. Each carries who said it, whether two people
     * disagree, whether it is half of a pair, and — at scoring only — whether it was right.
     */
    val badges: Map<CardRef, Badge> = emptyMap(),
    /**
     * The two cards a Jack or a Queen is being pointed at, for as long as it is being pointed.
     *
     * Present from the moment the action starts, with both ends empty, because the first
     * thing it has to say is that two cards are wanted. Null for every other action, which is
     * how the rail knows whether it is drawing an aim or the card in play.
     */
    val aim: Aim? = null,
    /**
     * Seats a bot is covering because their person has gone.
     *
     * It cannot come off the view: `isHuman` is inside the canonical state hash, so the room
     * never writes a takeover into the game state. Without it you are negotiating a final
     * round with somebody who left.
     */
    val away: Set<String> = emptySet(),
    /**
     * Claims the reveal has just proved wrong, at scoring and nowhere else.
     *
     * Separate from [badges] rather than folded into their text, because being wrong is a
     * different thing from what was said and the table draws it differently — and because the
     * app never adjudicates a claim while it could still matter.
     */
    val brokenClaims: Set<CardRef> = emptySet(),
    /**
     * The coalition's shared plan, open (design D7a): only while the player has the board up.
     *
     * A mode rather than a fixture of every table, because drawn beside the prompt it starved
     * the log strip. Every final-round table carries [planSummary] instead, and that is the
     * way in.
     */
    val board: Board? = null,
    /** The plan in one line, with the tap that opens the board. Null outside a final round. */
    val planSummary: PlanSummary? = null,
    /**
     * The plan's row on the live rail: what the plan says about the turn on play, as
     * information only. There for the whole final round — empty when nothing is planned —
     * so nothing under it moves; null while the plan itself is open, and outside a final round.
     */
    val planLine: PlanLine? = null,
)

/**
 * Which table the felt is drawing, and therefore what touching a card means (design D1).
 *
 * The plan cannot be an overlay: the felt would be showing live cards and ghosts at once and
 * lying about one of them. So it is a **mode**, and this is the whole of it — a function of
 * the table and of nothing else. No animation puts the screen into [PLAN] and none takes it
 * out, which is what makes "a tap in plan mode is never a move" a property that can be walked
 * over a whole round rather than a rule somebody has to remember at each call site.
 */
enum class TableMode {
    /** The table as it is. A tap is a move, resolved through [Table.taps]. */
    LIVE,

    /** The plan, open. A tap edits the coalition's draft and can never reach the engine. */
    PLAN,
}

/** Which of the two this table is. See [TableMode]. */
val Table.mode: TableMode get() = if (board != null) TableMode.PLAN else TableMode.LIVE

/** A card on the table: whose, and which slot. */
data class CardRef(val playerId: String, val position: Int)

/**
 * What a card wears on its back: the table's belief about it, and who put it there.
 *
 * [speakers] is what lets a claim be weighed without a tap — `knownCardPositions` is public,
 * so a badge from somebody who never read the card is visibly a guess. [disputed] means two
 * seats said things that cannot both be true, and the app never decides which; [paired] means
 * this is half of a "these two, in either order" claim and the other half wears the same
 * badge. [verdict] exists only at scoring, when the card is face up and the reveal has
 * refereed the claim (design D12).
 */
data class Badge(
    val text: String,
    val speakers: List<Speaker>,
    val disputed: Boolean = false,
    val paired: Boolean = false,
    val verdict: Verdict? = null,
    /**
     * A claim being **composed**, which nobody else can see yet.
     *
     * The rank rail is a multiple choice and the thing it is choosing about is on the felt, not
     * in the rail — so with nothing drawn until "Say it", a player naming three ranks had no
     * way to check what they were about to tell the table except by reading the plaques back.
     * Reported from a phone as the card not updating until afterwards.
     *
     * It has to be **visibly** provisional: a draft that looked like a claim would leave a
     * member unable to tell what they have actually said, which is worse than not drawing it.
     */
    val draft: Boolean = false,
)

/** How the reveal settled a claim. */
enum class Verdict { RIGHT, WRONG }

/**
 * A two-card action, half-aimed or fully aimed.
 *
 * Two named ends rather than a list, because the order is the whole of what the player is
 * tracking — the first card chosen and the one still to choose are different questions, and a
 * list of one leaves the reader to work out which end it is.
 */
data class Aim(val first: AimedCard? = null, val second: AimedCard? = null)

/**
 * One end of an aim: whose hand, which card along it (one-based, as a person counts along a
 * row), and its face — a real card for a Queen, which looks before it swaps, and
 * [CardView.Hidden] for a Jack, which does not. The projection has already decided which;
 * this only carries what it was given, so a Jack cannot leak a face by being drawn.
 */
data class AimedCard(val who: Speaker, val slot: Int, val card: CardView)

/** A button. */
data class Choice(val label: Label, val move: Move, val tone: Tone = Tone.NEUTRAL)

/**
 * A seat the table is offering: who they are, and what naming them does.
 *
 * [who] is how to *address* them — "You" for your own seat, which the coalition question can
 * offer — and [nickname] is who they *are*, which is a different question and the one a
 * portrait is keyed on. They differ for exactly one seat and that is the seat a face would
 * otherwise be missing from.
 */
data class SeatChoice(
    val id: String,
    val nickname: String,
    val who: Speaker,
    val move: Move,
    /**
     * True where naming the seat opens its turn of the plan rather than answering a question
     * about a card — the plate says "Plan Nina's turn" for one and the seat's name for the other.
     */
    val planTurn: Boolean = false,
)

/**
 * A rank the player may name.
 *
 * [muted] is a rank that is legal but unrewarding — a King may name an actionless rank on
 * purpose. [picked] is whether this rank is already in the claim being built, and is **null
 * where the rail is not building one**: a King's rail is fourteen sentences, each of which
 * sends on touch, while the claim rail is fourteen toggles. Somebody hearing the screen read
 * to them has to be able to tell those apart, so "not chosen" and "not a toggle" cannot be the
 * same value.
 */
data class RankChoice(
    val rank: Rank,
    val move: Move,
    val muted: Boolean = false,
    val picked: Boolean? = null,
)

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
    /**
     * A move that cannot reach the engine, as a type rather than as a check.
     *
     * Plan mode gives a tap on a card a meaning it never had — it edits the coalition's draft
     * — and the thing that must never happen is one of those taps turning into a move on the
     * real round (design D2). The guard is this interface: the plan composer is *declared* to
     * return a [Quiet], and [Send] is not one, so a routing bug that dispatched a `GameAction`
     * from a hypothetical table does not compile. `PlanIsQuietTest` reads the hierarchy back.
     */
    sealed interface Quiet : Move

    data class Send(val action: GameAction) : Move
    data class Ask(val question: Question) : Move, Quiet

    /**
     * Say something, rather than do something.
     *
     * A third kind because talk is a third kind: it changes no game state, nothing waits on
     * it, and a refusal is a lost sentence rather than a lost move. Folding it into [Send]
     * would have put a `TableTalk` where every call site expects a `GameAction` — and the
     * whole point of design D6 is that those two are not the same thing.
     */
    data class Say(val talk: TableTalk) : Move

    /**
     * Finish conferring.
     *
     * Its own case rather than a `Say`, because it is not a sentence: nothing is broadcast and
     * nothing is claimed. It says only that this seat has stopped talking.
     */
    data object Done : Move

    /**
     * Change one part of the coalition's shared plan (design D7a).
     *
     * A fourth kind, because a plan is a fourth kind of thing: not game state, not a sentence,
     * not a question the screen is asking itself — a shared draft the room keeps, which the
     * door in `CoalitionPlan.edited` decides about. Every other seat sees the result.
     */
    data class Plan(val edit: PlanEdit) : Move, Quiet

    /** Yes or no to the plan as a whole. A yes is also [Done]: agreeing is how you finish talking. */
    data class Agree(val agree: Boolean) : Move, Quiet
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

    /**
     * Final round: I am saying what I believe about somebody's cards.
     *
     * Built up by tapping rather than composed as a sentence, because there is no text input
     * in this game and because tapping the cards is how a person points at them anyway.
     * [about] is whose hand, [positions] the cards touched so far, [ranks] the ranks named so
     * far. One of each makes an exact claim; **two of each** leaves exactly one question — which
     * way round — and that question has three answers of equal standing, the third being
     * "not sure".
     *
     * "Not sure" is the whole point of the control, so it is one tap and never a mode. Ten
     * turns after setup the pair without its order is the commonest thing a person actually
     * has, and a vocabulary that cannot say it forces a guess the coalition then plans on.
     */
    data class Claiming(
        val about: String,
        val positions: List<Int> = emptyList(),
        val ranks: List<Rank> = emptyList(),
    ) : Question

    /**
     * Final round: the plan is open, and this is where it is being read from.
     *
     * Every field here is the **screen's**, never the plan's (design D3): which turn a member
     * is looking at is not part of the plan and must not travel, because two members reading
     * different turns of one plan is normal and a shared cursor would fight. `CoalitionPlan`
     * gains nothing for any of it.
     */
    data class ThePlan(
        /**
         * The transport's position (design D14): 0 is the table now, *k* is the table after the
         * plan's *k*th turn — and therefore the turn *k* names is the one being built, since a
         * stop named "Tide" is where Tide's turn has just happened.
         *
         * **Defaults to the first turn, not to now.** Opening the plan lands where there is
         * something to do; "now" is a place to go back to, not a place to start. At 0 there is
         * no turn to build, which is correct and is not where anybody should arrive.
         */
        val at: Int = 1,
        /** The card a select-then-select edit has picked up, if one has (design D5). */
        val picked: CardRef? = null,
        /**
         * The position the transport is travelling to, or null when it is parked.
         *
         * A *destination* rather than a flag, because the transport is a set of named stops
         * now: pressing ② from the table now means "play me the first two turns", and the
         * screen has to know where to stop as well as that it is going. It was a Boolean, and
         * a run always ran to the end — which is why there was no way to watch one turn.
         *
         * Editing sleeps while it is set (design D14): a card halfway between two seats is at
         * no position, so there is nothing to drop onto and nothing to drag.
         */
        val runningTo: Int? = null,
        /**
         * Whether the page's turn has just been watched, so the felt shows the table it leaves
         * rather than the one it starts from. Set by the runner when a film parks; cleared by
         * any swipe or touch, which is where the page's start table comes back.
         */
        val landed: Boolean = false,
    ) : Question {
        /** Whether the transport is moving. See [runningTo]. */
        val running: Boolean get() = runningTo != null
    }

    /**
     * Final round: what should [seat] do with the card their turn takes?
     *
     * The middle part of a turn — play it, keep it, or let it go — asked as its own question
     * because it is its own decision. A turn is a sequence and the sentence says it as one, so
     * each word of the sentence opens the choice that word is about.
     */
    data class Doing(val seat: String, val at: Int = 0) : Question

    /**
     * Final round: which of [seat]'s cards goes out when they keep the card their turn takes?
     *
     * Asked before anything lands on the board. "Keep it" used to write the seat's first card
     * onto the plan and leave the row to correct it — a guess, broadcast to the coalition as if
     * somebody had decided it. The answer is a card touched on the felt, or carried to the pile.
     */
    data class PuttingDown(val seat: String, val at: Int = 0) : Question

    /**
     * Final round: which rank the King at [part] of [seat]'s turn declares — the turn's own
     * King, a called one, a thrown one, or one a King pointed at. A rank off the rail.
     */
    data class Naming(val seat: String, val at: Int, val part: Part) : Question

    /**
     * Final round: which rank [seat] calls the card they put down as, off the rail of action
     * cards — the only way a card nobody has read can be made to act.
     *
     * Its own question rather than a [Naming] at [Part.Called], because that path is already
     * taken: `stepAt(Part.Called)` is what the *called card* does, and a called King naming a
     * rank is exactly that. The guess is not a step at a part; it is a word on the put-down.
     */
    data class Calling(val seat: String, val at: Int) : Question

    /**
     * Final round: the cards the action at [part] of [seat]'s turn names, touched on the felt —
     * the two a Jack or a Queen trades, the one a 7 to 10 looks at, the one a King points at.
     * [picked] is the first of two, already up (design D5).
     */
    data class Aiming(val seat: String, val at: Int, val part: Part, val picked: CardRef? = null) : Question

    /** Final round: who the Ace at [part] of [seat]'s turn makes draw. A seat off the felt or the rail. */
    data class Forcing(val seat: String, val at: Int, val part: Part) : Question

    /**
     * Final round: the [index]th throw-in on [seat]'s turn — who throws, and what rank.
     *
     * **Any coalition seat, not only your own.** The plan is one shared thing — every member
     * reads the same board — so who throws in on a turn is a part of that turn like any other,
     * said in the sentence beside it. [thrower] defaults to the viewer, since "I will throw one
     * in" is the common case, and the plates are the way to say somebody else will. An [index]
     * past the end adds a throw; one inside the list changes that throw.
     */
    data class Throwing(val seat: String, val at: Int, val index: Int, val thrower: String? = null) : Question
}

/** How many cards a single claim may pair. Three would be six orderings, which nobody reads. */
private const val CLAIM_PAIR = 2

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

/**
 * @param seen the viewer's own lane as it was when they last had the plan open, so the header
 *   can say a teammate has changed their turn since. See [PlanSummary.changedBy].
 */
@Suppress("LongParameterList")
fun tableFor(
    view: PlayerView,
    question: Question = Question.None,
    away: Set<String> = emptySet(),
    offered: TableTalk.Proposal? = null,
    plan: CoalitionPlan? = null,
    reveals: List<PublicReveal> = emptyList(),
    seen: Lane? = null,
): Table = tableBody(view, question, plan, away, reveals)
    .let { table ->
        table.copy(
            away = away,
            planSummary = summaryFor(view, plan, seen),
            planLine = if (table.board == null) planLineFor(view, plan) else null,
        )
    }
    .offering(offered, view)

/**
 * A move somebody has suggested to this seat, as something to accept or decline.
 *
 * The rule the whole design turns on lives here, in one line: accepting sends **the viewer's
 * own** `GameAction`. A proposal carries no authority, is never validated as the proposer's
 * and never reduced — what reaches the engine is the recipient's move, seat-bound like any
 * other. That is what lets a person and a bot be addressed identically, and it is why nothing
 * anywhere acts for another seat.
 *
 * Declining is offered beside it and is *said*, not swallowed: a proposal that could only be
 * ignored would leave the proposer watching nothing happen and unable to tell whether it had
 * even arrived.
 */
private fun Table.offering(proposal: TableTalk.Proposal?, view: PlayerView): Table {
    if (proposal == null || proposal.to != view.viewerId) return this

    // **First**, not appended. When the viewer's turn opens on a move they have already
    // agreed to, that move is what they came to make — burying it under the ordinary
    // openings would make an agreed suggestion slower to act on than an unagreed one, which
    // is the wrong way round for the only thing here that saves anybody a decision.
    //
    // Every other move stays exactly where it was. Pre-arming aims the turn; it never
    // narrows it, and a player who changes their mind between agreeing and playing has lost
    // nothing.
    return copy(
        choices = listOf(
            Choice(Label.DoAsSuggested, Move.Send(proposal.move)),
            Choice(
                Label.DeclineSuggestion,
                Move.Say(
                    TableTalk.Answer(view.viewerId, proposal.by, TableTalk.Answer.Says.NO),
                ),
            ),
        ) + choices,
    )
}

/**
 * The table proper. Split from [tableFor] so that [Table.away] is applied once, at the single
 * exit, rather than at each of the dozen returns below — a new branch that forgot it would
 * silently drop the label off a seat somebody had left.
 */
@Suppress("ReturnCount", "LongParameterList")
private fun tableBody(
    view: PlayerView,
    question: Question,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal>,
): Table {
    val me = view.players.firstOrNull { it.id == view.viewerId }
        ?: return Table(prompt = Ask.Watching, waiting = true)

    if (view.phase == GamePhase.SCORING) return scoringTable(view).showing(view)
    if (view.phase == GamePhase.SETUP) {
        return setupTable(view, me.id, me.knownCardPositions).showing(view)
    }

    // The board is open, or a lane of it is being composed. Above the window too: planning is
    // what the window is for, and both take the table over the way a claim does.
    // `showing` reads the view for which cards may be turned over and which wear a claim, and
    // the plan draws a *different* table — the one the transport is parked on. Read off that,
    // or a King that emptied a rank leaves every badge behind it one card out of step.
    planTable(view, question, plan, away, reveals)?.let { return it.showing(it.board?.felt ?: view) }

    // The player is saying what they believe about somebody's cards.
    //
    // **Above the confer window, and it used to be below it.** The window offers a tap on every
    // claimable card and that tap asks exactly this question — so with the window's table
    // winning, a member who touched a card got the window back and nothing else. The taps were
    // there, the picker they opened was unreachable, and the felt gave no sign either way:
    // "not clear how I should declare mine or other cards" is that bug, reported. The picker
    // *is* what the window is for, so it takes the table over the way the plan does.
    if (question is Question.Claiming && mayDeclare(view)) {
        return claimingTable(view, question).showing(view).drafting(view, question)
    }

    // **A card on the pile, before the window the call opened.**
    //
    // Vinto is declared at the *end* of a turn, and that turn ended with a card landing face up
    // — so the throw and the call arrive together. With the confer window winning, the moment a
    // bot called, the screen became the coalition's planning band: the window was open at the
    // engine, the cards still matched, and there was no way on the screen to use either.
    //
    // The throw goes first because it is **priced and timed**. It belongs to the whole table at
    // once, a wrong guess costs a card and bars the seat for the rest of the final round, and it
    // closes. The confer window has no clock in a solo game — it ends when the person says it
    // does — so it loses nothing by waiting, and the plan it is for is about turns that have not
    // happened yet.
    tossInTable(view)?.let { return it.showing(view) }

    // The coalition's window: talk only, and a way out of it. Before the round's first turn,
    // so it comes above every table below — a window a player cannot see or end is a stall.
    if (view.conferMsRemaining != null && mayDeclare(view)) {
        return conferringTable(view, plan).showing(view)
    }

    val pending = view.pendingAction
    if (pending != null && pending.playerId == view.viewerId) {
        return pendingTable(view, pending, question).showing(view)
    }

    val current = view.players.getOrNull(view.currentPlayerIndex)
    if (current?.id != view.viewerId || pending != null) {
        val watching = Table(prompt = Ask.SomebodyIsPlaying(playing(current, view)), waiting = true)
        // A coalition member waiting through the final round can still talk: tapping one of
        // their own cards opens the claim picker.
        val talk = declareTaps(view)
        return if (talk.isEmpty()) {
            watching.showing(view)
        } else {
            watching.copy(detail = Detail.TapACardToSayWhatItIs, taps = talk).showing(view)
        }
    }

    return turnStartTable(view).showing(view)
}

/**
 * The board, open, or a lane of it being composed — or null when neither is what the screen is
 * asking. The caller may open the board to read it, and gets nothing to tap on it; only a
 * coalition member may compose.
 */
private fun planTable(
    view: PlayerView,
    question: Question,
    plan: CoalitionPlan?,
    away: Set<String>,
    reveals: List<PublicReveal>,
): Table? = when {
    question is Question.ThePlan && view.phase == GamePhase.FINAL ->
        boardTable(view, plan, away, reveals, question)

    question is Question.Doing && mayDeclare(view) -> doingTable(view, question, plan, away, reveals)
    question is Question.PuttingDown && mayDeclare(view) ->
        puttingDownTable(view, question, plan, away, reveals)
    question is Question.Naming && mayDeclare(view) -> namingTable(view, question, plan, away, reveals)
    question is Question.Calling && mayDeclare(view) -> callingTable(view, question, plan, away, reveals)
    question is Question.Aiming && mayDeclare(view) -> aimingTable(view, question, plan, away, reveals)
    question is Question.Forcing && mayDeclare(view) -> forcingTable(view, question, plan, away, reveals)
    question is Question.Throwing && mayDeclare(view) -> throwingTable(view, question, plan, away, reveals)
    else -> null
}

/**
 * The claim taps the final round carries — the whole of what "talking to your coalition" is in
 * this app, since nothing a player types ever reaches another player.
 *
 * **Any** seat's cards, not only the speaker's own. A member who peeked the caller's third
 * card, or a teammate's, has somewhere to put it — which is what the bots always had through
 * their own pooled sightings and a person did not.
 *
 * Offered on **both** kinds of turn, which is the correction: talking is not a turn, so it
 * costs none, takes none, and can be done as often as the player likes.
 *
 * Never for the caller, who has no coalition to inform and may speak only of their own hand;
 * and never inside a toss-in window, which is the one time a tap on a card already means
 * something else — that table is built above both call sites and returns before either.
 */
private fun declareTaps(view: PlayerView): Map<CardRef, Move> {
    if (!mayDeclare(view)) return emptyMap()
    return view.players
        .flatMap { seat ->
            seat.cards.indices.map { position ->
                CardRef(seat.id, position) to
                    Move.Ask(Question.Claiming(seat.id, listOf(position)))
            }
        }
        .toMap()
}

/**
 * Table talk is for coalition members, for the whole of the final round.
 *
 * It used to wait on a leader being chosen, which made the retired vote the thing that
 * *unlocked speech* — and the seat after the caller is on play the instant the round starts,
 * so a player could arrive at their own turn with no way to tell anybody anything. Talking is
 * not a turn: it costs none, takes none, and can be done as often as the player likes.
 */
private fun mayDeclare(view: PlayerView): Boolean =
    view.phase == GamePhase.FINAL &&
        view.vintoCallerId != null &&
        view.viewerId != view.vintoCallerId

/**
 * The moment before the final round runs, which belongs to the coalition.
 *
 * Everything the round's talk needs is here: every card is tappable, so a claim about anybody
 * can be made, and one button ends the declaring. It is a *table*, not a modal — the felt stays
 * readable underneath, because what a coalition is deciding is written on it.
 *
 * **The button goes to the plan and starts nothing.** It used to be `Move.Done`, which ended
 * this seat's share of the window and set three final turns going — so a player who meant only
 * "that is everything I can remember" watched the round run, and was then put back on the plan
 * board, where the felt draws the plan's table rather than the live one and a card the plan has
 * yet to draw is rose. A real move, then a pink ghost, out of one press; reported from a phone
 * and held by `HumanCoalitionMemberTest.sayingThatIsAllYouKnowOpensThePlanAndStartsNothing`.
 * Starting the turns is the plan's own button now, and this one is a question.
 */
private fun conferringTable(view: PlayerView, plan: CoalitionPlan?): Table {
    val claimable = declareTaps(view)
    return Table(
        prompt = Ask.SayWhatYouKnow,
        // "Tap one of your cards to say what you think it is" — but only where there is a card
        // to tap. With nothing claimable the sentence pointed at nothing, on a felt where the
        // instruction is in the rail and the thing it names is on the table. The cards that can
        // be claimed wear a ring for as long as the question stands; when none can, the
        // instruction goes with them rather than sending somebody hunting.
        detail = Detail.TapACardToSayWhatItIs.takeIf { claimable.isNotEmpty() },
        // Where this hand stands, then the way out. The three come first because they are what
        // the window is *for*: the coalition is scored on its lowest hand, and until the table
        // knows whose that is, nothing else anybody says here can be acted on.
        // Green, like every other move that gets on with the game: both controls in this window
        // are ones a player presses to make something happen, and the tones are muscle memory
        // (`Tone`). Slate said "decline", which is what neither of them is.
        choices = listOf(
            Choice(
                Label.ThatsAllIKnow,
                Move.Ask(Question.ThePlan(at = openingStop(view, plan))),
                Tone.PLAY,
            ),
        ),
        taps = claimable,
        waiting = false,
    )
}

/**
 * Saying what you believe, built by tapping rather than composed as a sentence.
 *
 * One table, two rails and one rule: **touch the cards you mean, then touch every rank they
 * could be.** Nothing is sent until the player says it is finished, which is what lets one
 * control carry the whole of what a person actually remembers:
 *
 *  - one card, one rank — "my third is a King", the claim that was already reachable;
 *  - one card, several ranks — "it is a 7 or an 8", which is what memory usually holds ten
 *    turns after setup and which the rail could not say at all, because it sent on the first
 *    rank touched. `Claim` has carried it since the day it was written;
 *  - two cards, two ranks — the pair, which leaves exactly one question, and the three answers
 *    to it sit under the rail rather than on a screen of their own. The rail stays live beneath
 *    them: the order is a *refinement* of the ranks named, so changing one must not mean
 *    starting again;
 *  - two cards, any other number of ranks — "those two are both low", "they are among these
 *    three". No order to settle, so none is asked for.
 *
 * At most [CLAIM_PAIR] cards, because three is six orderings and nobody reads six. Ranks have
 * no such limit: naming all fourteen is [Claim.vacuous], which is the take-back.
 *
 * A speaker may also unsay what they said, and **only about the cards under their finger** —
 * see [takingBack].
 */
private fun claimingTable(view: PlayerView, question: Question.Claiming): Table {
    val seat = view.players.firstOrNull { it.id == question.about } ?: return Table(Ask.Watching)
    val picked = question.positions.sorted()
    val named = question.ranks
    // Two cards and two ranks: the only thing left to say is the order, and one of the answers
    // is that there isn't one. Positions sorted, so "first" on a button means the left-hand
    // card on the felt rather than whichever the player happened to tap first.
    val pair = picked.size == CLAIM_PAIR && named.size == CLAIM_PAIR

    return Table(
        prompt = when {
            picked.isEmpty() -> Ask.SayWhatYouKnow
            pair -> Ask.WhichWayRound
            picked.size > 1 -> Ask.WhatDoYouSayTheseCardsAre
            else -> Ask.WhatDoYouSayThisCardIs
        },
        // The rail's instruction while there is a rail; otherwise the felt's, because with the
        // last card taken back out there is nothing to name ranks *for* and the next touch is
        // on a card again.
        detail = if (picked.isEmpty()) Detail.TapACardToSayWhatItIs else Detail.NameEveryRankItCouldBe,
        choices = buildList {
            when {
                pair -> addAll(orderings(view, question.about, picked, named))
                picked.isNotEmpty() && named.isNotEmpty() -> add(
                    Choice(
                        label = Label.SayIt,
                        tone = Tone.PLAY,
                        move = Move.Send(
                            saying(
                                view.viewerId,
                                seat.id,
                                // Covering where the claim names as many ranks as cards: "this
                                // one is a King". Fewer ranks than cards, or more, and each card
                                // is merely *one of* them — which is a different sentence and the
                                // one the wider claims are for.
                                Claim(view.viewerId, picked, named, covering = named.size == picked.size),
                            ),
                        ),
                    ),
                )
            }
            if (mineHere(view, seat, picked)) {
                add(Choice(Label.Withdraw, Move.Send(takingBack(view.viewerId, seat.id, picked))))
            }
            add(Choice(Label.Back, Move.Ask(Question.None)))
        },
        // Taking the last card back out returns the whole felt, not just this seat's row: the
        // question is open again, and the prompt above says so. Reaching another hand used to
        // mean finding "Back" first.
        taps = if (picked.isEmpty()) declareTaps(view) else claimTaps(seat, question, question.positions),
        ranks = claimRanks(question, picked),
    )
}

/**
 * The claim being composed, drawn on the cards it is about before anybody else can see it.
 *
 * Applied **after** [showing], so it sits on top of whatever the table already believes: while
 * a member is changing their mind about a card, what they are changing it *to* is the thing
 * worth reading, and the old word is one tap away again if they go back.
 *
 * Carried as [Badge.draft] rather than as an ordinary badge because the difference is the whole
 * point — a draft that looked like a claim would leave a member unable to tell what they have
 * actually told the table.
 */
private fun Table.drafting(view: PlayerView, question: Question.Claiming): Table {
    val positions = question.positions
    if (positions.isEmpty()) return this

    // No rank named yet is still worth drawing: it says *this* is the card the rail is about,
    // which is otherwise only knowable by remembering which one was tapped.
    val text = question.ranks.joinToString("/") { it.serialName }.ifEmpty { UNNAMED }
    val draft = positions.associate { position ->
        CardRef(question.about, position) to Badge(
            text = text,
            speakers = listOf(speakerFor(view, view.viewerId)),
            paired = positions.size > 1,
            draft = true,
        )
    }
    return copy(badges = badges + draft)
}

/** A card the claim names before any rank does: the mark that says "this one". */
private const val UNNAMED = "?"

/** Tapping a card adds it to the claim; tapping it again takes it back out. */
private fun claimTaps(
    seat: PlayerSeatView,
    question: Question.Claiming,
    picked: List<Int>,
): Map<CardRef, Move> = seat.cards.indices
    .mapNotNull { position ->
        val next = if (position in picked) picked - position else picked + position
        // Three cards would be six orderings, which is not a question anybody reads.
        next.takeIf { it.size <= CLAIM_PAIR }?.let {
            CardRef(seat.id, position) to Move.Ask(question.copy(positions = it))
        }
    }
    .toMap()

/**
 * The rank rail: **the whole of it, every time, as toggles.**
 *
 * It used to send the moment a single card had a single rank, which made the rail a list of
 * fourteen sentences rather than a way of building one — and made "a 7 or an 8" unsayable. A
 * toggle costs the exact claim one extra touch and buys every partial one, which is the trade
 * worth making: exact is what a player has about two cards, and partial is what they have
 * about the rest.
 */
private fun claimRanks(question: Question.Claiming, picked: List<Int>): List<RankChoice> {
    if (picked.isEmpty()) return emptyList()
    return ALL_RANKS.map { rank ->
        val held = rank in question.ranks
        RankChoice(
            rank = rank,
            move = Move.Ask(
                question.copy(ranks = if (held) question.ranks - rank else question.ranks + rank),
            ),
            picked = held,
        )
    }
}

/** The one question a pair leaves: which way round — or neither, which is an answer. */
private fun orderings(
    view: PlayerView,
    about: String,
    positions: List<Int>,
    ranks: List<Rank>,
): List<Choice> = listOf(
    // An assigned pairing is simply two exact claims, and one action carries both — so
    // "which way round" costs the same one tap that "not sure" does.
    Choice(
        Label.ThisWayRound(positions[0], ranks[0], positions[1], ranks[1]),
        Move.Send(
            saying(
                view.viewerId,
                about,
                exact(view.viewerId, positions[0], ranks[0]),
                exact(view.viewerId, positions[1], ranks[1]),
            ),
        ),
    ),
    Choice(
        Label.ThisWayRound(positions[0], ranks[1], positions[1], ranks[0]),
        Move.Send(
            saying(
                view.viewerId,
                about,
                exact(view.viewerId, positions[0], ranks[1]),
                exact(view.viewerId, positions[1], ranks[0]),
            ),
        ),
    ),
    // Third, and equal: the answer most people actually have.
    Choice(
        Label.NotSureWhichWayRound,
        Move.Send(
            saying(
                view.viewerId,
                about,
                Claim(view.viewerId, positions, ranks, covering = true),
            ),
        ),
    ),
)

internal fun saying(speaker: String, about: String, vararg claims: Claim) =
    GameAction.DeclareCards(DeclareCardsPayload(speaker, about, claims.toList()))

private fun exact(speaker: String, position: Int, rank: Rank) =
    Claim(speaker, listOf(position), listOf(rank))

/**
 * Unsaying what you said about **these** cards, and nothing else.
 *
 * A claim naming every rank narrows nothing, so belief reads straight past it
 * ([Claim.vacuous]) and the speaker's earlier word on those positions is superseded — which is
 * the model's own per-card take-back, and is what the bots have always used to answer a
 * contradiction about one card.
 *
 * The button sent the *empty claim list* instead, which unsays everything about the whole
 * hand. It sits in a picker scoped to one card, so correcting a single word silently cost a
 * player every other thing they had told the coalition about that seat.
 */
private fun takingBack(speaker: String, about: String, positions: List<Int>) =
    saying(speaker, about, Claim(speaker, positions, ALL_RANKS, covering = false))

/** Whether the viewer has anything of their own standing on the cards they have picked. */
private fun mineHere(view: PlayerView, seat: PlayerSeatView, positions: List<Int>): Boolean =
    seat.claims.any { it.by == view.viewerId && it.positions.any { p -> p in positions } }

/**
 * What the table currently believes about each card, worn on the card for everyone to read.
 *
 * A card nobody has spoken about wears nothing. One rank means the table agrees; several mean
 * somebody is unsure or two people disagree, and a disputed card is marked as such rather than
 * quietly resolved — the app never decides which claimant was right.
 */
private fun declaredBadges(view: PlayerView): Map<CardRef, Badge> =
    view.players
        .flatMap { seat ->
            seat.cards.indices.mapNotNull { position ->
                val believed = believedOnView(seat, position)
                if (believed.sources.isEmpty()) return@mapNotNull null
                val separator = if (believed.disputed) "?" else "/"
                // Refereed only where the card is actually face up: a claim about a card
                // nobody can see is not wrong, and calling it so would be the app adjudicating.
                val shown = (seat.cards[position] as? CardView.Visible)?.card
                    ?.takeIf { view.phase == GamePhase.SCORING }
                CardRef(seat.id, position) to Badge(
                    text = believed.candidates.joinToString(separator) { it.serialName },
                    speakers = believed.sources.map { speakerFor(view, it.by) }.distinct(),
                    disputed = believed.disputed,
                    paired = believed.sources.any { it.covering && it.positions.size > 1 },
                    verdict = shown?.let { card ->
                        if (card.rank in believed.candidates) Verdict.RIGHT else Verdict.WRONG
                    },
                )
            }
        }
        .toMap()

/**
 * [believedAt] over a *view*'s seat rather than a `PlayerState`.
 *
 * The engine's version takes the authoritative seat; a screen only ever has the redacted one,
 * and the claims on it are already the standing ones the room chose to send. Same combining
 * rule, so the table and the plan cannot come to different conclusions about a card.
 */
internal fun believedOnView(seat: PlayerSeatView, position: Int): Believed {
    // A claim naming every rank is a claim taken back (`Claim.vacuous`), and is not a source.
    val about = seat.claims.filter { position in it.positions && !it.vacuous }
    // `ALL_RANKS`, not an empty set: a card nobody has spoken about is *every* rank, which is
    // the same as knowing nothing and is what `believedAt` returns for it. An empty set made
    // `Believed.value` divide by zero; every caller today happens to guard on `sources`, and
    // the next one would not have.
    if (about.isEmpty()) return Believed(ALL_RANKS.toSet(), disputed = false, sources = emptyList())
    val sets = about.map { it.ranks.toSet() }
    val agreed = sets.reduce { left, right -> left intersect right }
    return if (agreed.isEmpty()) {
        Believed(sets.reduce { left, right -> left union right }, disputed = true, sources = about)
    } else {
        Believed(agreed, disputed = false, sources = about)
    }
}

private fun Table.showing(view: PlayerView): Table {
    val revealed = revealedTo(view)

    // A claim is worn on a card's back, so mid-round it comes off the moment a face turns up:
    // the card says what it is, and the claim beside it is either the same word twice or a
    // contradiction nobody can act on yet.
    //
    // **Scoring is the exception, and it is the point.** Every hand goes face up at once, so
    // that is when every claim can finally be checked — which is what gives table talk a cost
    // and an honest claim its worth. Nothing is checked when it is *made*; the reveal is the
    // referee, and it referees the caller's bluffs on exactly the same terms.
    val settling = view.phase == GamePhase.SCORING
    val badges = if (settling) declaredBadges(view) else declaredBadges(view).filterKeys { it !in revealed }

    return copy(
        revealed = revealed,
        help = helpFor(view),
        badges = badges,
        brokenClaims = if (settling) claimsTheRevealContradicts(view) else emptySet(),
    )
}

/**
 * The claims the turned-over hands prove wrong.
 *
 * Only at scoring, and only where the card is actually visible — a claim is not "wrong"
 * because nobody can see the card, and calling it wrong would be the app adjudicating, which
 * it never does while a round is running.
 *
 * A partial claim counts as true if the real rank is among its candidates: "a King or a Queen"
 * about a Queen was a useful thing to say, not a miss.
 */
private fun claimsTheRevealContradicts(view: PlayerView): Set<CardRef> =
    view.players
        .flatMap { seat ->
            seat.cards.mapIndexedNotNull { position, card ->
                val shown = (card as? CardView.Visible)?.card ?: return@mapIndexedNotNull null
                val believed = believedOnView(seat, position)
                CardRef(seat.id, position)
                    .takeIf { believed.sources.isNotEmpty() && shown.rank !in believed.candidates }
            }
        }
        .toSet()

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

    // Your own final-round turn is still a turn you can talk through, and the rule under the
    // prompt is what says so — the taps are invisible until something names them.
    val talk = declareTaps(view)
    return Table(
        prompt = Ask.YourTurn,
        choices = choices,
        taps = talk,
        detail = Detail.TapACardToSayWhatItIs.takeIf { talk.isNotEmpty() },
    )
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
        // A hand with nothing in it has no slot to choose and no card going out to guess at, so
        // the question is skipped and the button says what happens. Asking anyway opened a
        // screen with no cards on it and no way forward.
        choices += if (view.players.first { it.id == me }.cards.isEmpty()) {
            Choice(Label.KeepIt, Move.Send(GameAction.SwapCard(SwapCardPayload(me, 0))), Tone.KEEP)
        } else {
            Choice(Label.SwapCards, Move.Ask(Question.WhichSlot), Tone.KEEP)
        }
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
    Table(
        prompt = prompt,
        choices = listOf(giveUp(view.viewerId)),
        taps = anyTaps(view, pending),
        detail = whatItIs(pending),
        aim = aimedSoFar(view, pending),
    )
} else {
    Table(
        prompt = Ask.SwapThem,
        choices = listOf(
            Choice(Label.SwapCards, Move.Send(swap), Tone.PLAY),
            Choice(Label.LeaveThem, Move.Send(leave)),
        ),
        detail = whatItIs(pending),
        aim = aimedSoFar(view, pending),
    )
}

/**
 * The cards already chosen, and the room left for the ones that are not.
 *
 * A chosen card wears the gold ring, and on a crowded table that ring is the only record of
 * what a Jack or Queen is pointed at. This is the other record, and it used to be a line of
 * words — "Chosen: You, card 3 and Don, card 5" — under a rail column spending its one card
 * of room on the Queen doing the pointing. The player already knows about the Queen; they
 * chose to play it, and it is lying on the discard. What they are deciding about is the pair,
 * so the pair is what the column draws.
 */
private fun aimedSoFar(view: PlayerView, pending: PendingActionView): Aim = Aim(
    first = pending.targets.getOrNull(0)?.let { aimedCard(view, it) },
    second = pending.targets.getOrNull(1)?.let { aimedCard(view, it) },
)

private fun aimedCard(view: PlayerView, target: PendingTargetView) = AimedCard(
    who = speakerFor(view, target.playerId),
    slot = target.position + 1,
    card = target.card,
)

/**
 * What the card running this action is, now that it is no longer drawn beside the words.
 *
 * The rail used to say this by showing the card; the aim has the column, so the sentence has
 * to carry it.
 *
 * A King needs no special case, which is worth saying because it looks as though it should.
 * Naming a card correctly takes that card out of the hand and makes *it* the pending card, so
 * a King that fetched a Jack reads "Jack, worth 10: swap any two…" — the borrowed card, which
 * is the one being aimed. The King is already on the discard. [withBorrowed] covers the other
 * shape, where a pending action carries a `declaredRank` of its own.
 */
private fun whatItIs(pending: PendingActionView): Detail? =
    (pending.card as? CardView.Visible)?.card?.rank?.let(Detail::WhatTheCardDoes)

internal fun speakerFor(view: PlayerView, playerId: String): Speaker =
    if (playerId == view.viewerId) {
        Speaker.You
    } else {
        view.players.firstOrNull { it.id == playerId }?.nickname
            ?.let(Speaker::Named)
            ?: Speaker.Nobody
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

/** An Ace in the final round can only cost the coalition, the caller being untouchable. */
private fun aceIsATrap(view: PlayerView): Boolean =
    view.phase == GamePhase.FINAL && view.vintoCallerId != null && view.viewerId != view.vintoCallerId

/**
 * The only action that names a player rather than a card.
 *
 * It still aims at the felt, because what an Ace lands on is a *hand*: every card of every
 * opponent sends the same move, so the target is the size of five cards rather than of the
 * chip beside them, and the hand lights up as one — a tappable card wears the ring that says
 * so. There is no card to *choose*; the victim draws one nobody has seen. Which is why the
 * rail carries the seats as well, for the player who reads the question before the table.
 */
private fun forceDrawTable(view: PlayerView): Table = Table(
    prompt = Ask.WhoDrawsACard,
    taps = view.players
        .filter { it.id != view.viewerId }
        .flatMap { seat ->
            val name = GameAction.SelectActionTarget(
                SelectActionTargetPayload.Ace(view.viewerId, seat.id),
            )
            seat.cards.indices.map { position -> CardRef(seat.id, position) to Move.Send(name) }
        }
        .toMap(),
    // The rail no longer draws the Ace while it asks — the player drew it, read it and chose
    // to play it, and a fourth showing is not news — so the sentence carries what the picture
    // used to. Without this the question arrives with no statement of what saying yes does.
    //
    // In the final round it says something more particular, because an Ace there is a trap:
    // every legal target is a **teammate**, the caller being off limits, so it hands a penalty
    // card to one's own side and can land on the very hand still able to win the round. The
    // bots have always known — `CoalitionPlanner` never plays one from hand and puts a
    // tossed-in one down rather than aiming it — and a person was asked the same question with
    // no guidance and three bad answers.
    detail = if (aceIsATrap(view)) Detail.AnAceOnlyHurtsYourOwnSide else Detail.WhatTheCardDoes(Rank.ACE),
    // Putting it down leads, for the same reason. Every legal target stays on offer: the rule
    // is the player's to break if they want it.
    choices = listOf(giveUp(view.viewerId)),
    seats = view.players.filter { it.id != view.viewerId }.map { seat ->
        SeatChoice(
            id = seat.id,
            nickname = seat.nickname,
            who = speakerFor(view, seat.id),
            move = Move.Send(
                GameAction.SelectActionTarget(
                    SelectActionTargetPayload.Ace(view.viewerId, seat.id),
                ),
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

/**
 * What a toss-in window is asking this seat, or null when it is asking nothing.
 *
 * The window **reopens after every card thrown into it is played** — the engine puts the
 * sub-phase back to `toss_queue_active` and clears the ready list, so a table that has just
 * watched three nines resolve is asked about nines again. Reported from a phone: *"I already
 * responded that no tossin from me… rank list did not change, why?"*
 *
 * The engine is not what has to change. `playersReadyForNextTurn` is inside the canonical hash
 * and not re-asking diverges **48 of the 50** parity recordings, so what the engine says stays
 * what it says and the app stops making a person say it twice: the screen remembers the answer
 * it gave and gives it again, unasked, when the same question comes back.
 *
 * Same means **same to this seat**: the ranks it is about, and this seat's own hand as this seat
 * can see it. A thrown King widens the ranks and a Jack moves cards, and either is a genuinely
 * new question. So is a card this seat has *learned* since — a seven thrown in to peek at your
 * own row can turn up the second nine you are being asked about — which is why the hand is
 * compared as the viewer sees it rather than by its size.
 *
 * **Never for the window this seat owns.** There the Continue button is also the last "I am not
 * calling Vinto" before the turn passes, and a call spent silently is worse than one more press
 * (product owner).
 */
fun tossInAsk(view: PlayerView): TossInAsk? {
    val toss = view.activeTossIn ?: return null
    if (!view.tossInIsOpen) return null

    val me = view.viewerId
    if (me in toss.playersReadyForNextTurn) return null
    if (view.players.getOrNull(toss.originalPlayerIndex)?.id == me) return null

    val hand = view.players.firstOrNull { it.id == me }?.cards ?: return null
    return TossInAsk(ranks = toss.ranks.toList(), hand = hand)
}

/** One toss-in window as a question, so the same one asked twice can be recognised. See [tossInAsk]. */
data class TossInAsk(val ranks: List<Rank>, val hand: List<CardView>)

private fun tossInTable(view: PlayerView): Table? {
    val toss = view.activeTossIn ?: return null
    if (!view.tossInIsOpen) return null

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
internal val PendingActionView.canGoToHand: Boolean
    get() = from == PendingCardOrigin.DRAWING
