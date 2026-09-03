package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType

/**
 * The nine things there are to learn, in the order `VINTO_RULES.md` puts them.
 *
 * One per heading of the rules rather than one per mechanic, because that is the shape a
 * player will meet the game in again if they ever read them: the table, the deal, taking a
 * card, keeping or throwing it, naming a rank, what the cards do, throwing in, calling Vinto,
 * and how the round is scored.
 */
/**
 * The parts of the game the lesson covers, one dot each.
 *
 * It used to carry an English `label` — which nothing rendered. Nine strings in a module with
 * no resources, kept for a display that never happened, while the dots they were written for
 * had no accessible name at all: a screen reader was given nine unlabelled circles. The words
 * are in `composeApp`'s `Labels.kt` now (the same place `Difficulty` and `Pace` keep theirs)
 * and the dots use them.
 */
enum class Chapter {
    TABLE,
    PEEK,
    DRAW,
    KEEP,
    DECLARE,
    ACTIONS,
    TOSS,
    VINTO,
    SCORE,
}

/**
 * Something the coach can point at.
 *
 * A place on the table is an [Anchor], which the screen already knows where to find — every
 * card, the deck, the pile and the pending slot report their positions as they lay out. The
 * rest is furniture with a name: a button by its label, a rank chip, a seat plate, or a piece
 * of the screen's own scaffolding.
 */
sealed interface Target {
    data class Place(val anchor: Anchor) : Target
    data class Seat(val playerId: String) : Target

    /**
     * A button, named by what it *is* rather than by what it says.
     *
     * It was a `String` — the button's English — which meant the lesson identified a control
     * by text that a translation would change and a rewording would break. It already had:
     * the "two ways to start a turn" beat looked for a label beginning "Take the" while the
     * model produced "Use Queen", so a beat the director deliberately sets up (UI.md §6g) never
     * fired and nothing said so.
     */
    data class Button(val label: Label) : Target
    data class Chip(val rank: Rank) : Target

    /** Screen furniture: the deck badge, the recent-actions box, the "?" button. */
    data class Furniture(val id: String) : Target

    companion object {
        const val BADGE = "badge"
        const val LOG = "log"
        const val HELP = "help"
        const val TOSS_SLOT = "toss"
    }
}

/** What the coach is saying, and what it is pointing at while it says it. */
data class Lesson(
    val chapter: Chapter,
    /**
     * Which beat this is — the message, not the words.
     *
     * It used to be a `title: String?` and a `body: String` assembled here, which meant the
     * lesson was English whatever the phone was set to (WORDS.md §6h). One field now: a [Teaches] carries
     * its own identity and whatever varies within it, and the UI renders both halves from
     * resources. Two beats have no heading at all, and that stays true — it is the *renderer*
     * that answers null for a title, because "does this beat have a heading" is a fact about
     * the words rather than about the lesson.
     *
     * The old comment on `title` is worth keeping, because it is a rule for whoever writes the
     * next one: the title is never what to do. The control panel underneath already gives the
     * instruction ("Look at two of your cards"), and a coach that repeats it in its own words
     * has spent the top line of the rail on a sentence the player has already read. The title
     * is the reason, the consequence, or the thing nobody would guess — "The only look you
     * get".
     */
    val teaches: Teaches,
    val point: Target? = null,
    /**
     * Set when the lesson is something to *read* rather than something to do: the screen shows
     * a Continue button and nothing moves until it is pressed. A card game explained at the
     * speed of a card game is not explained.
     *
     * The beat's own id, for the beats that are talk beats, and null for the ones derived from
     * the position — which is why it is still a separate field rather than read off [beat].
     */
    val talkId: String? = null,
    /**
     * Cards to hold up while this is being said.
     *
     * A rank explained in words is a rank the player then has to match against a picture on
     * the felt. The lesson shows the cards it is talking about.
     */
    val cards: List<Rank> = emptyList(),
    /**
     * A card the player is meeting for the first time, said in the game's own words.
     *
     * The rank rather than the sentence: the words are `CARD_CONFIGS` — the same copy the help
     * sheet and the web app show — and the frame around them ("Queen — worth 10. …") is a
     * resource like everything else here.
     */
    val noteRank: Rank? = null,
    /** What a glow or a ring on the table means, said once, the first time it appears. */
    val gloss: Gloss? = null,
)

/**
 * What the coach says the first time somebody ignores it.
 *
 * Once, without reproach, and never again. The pointer is a suggestion and the lesson is
 * derived from the position, so a player who does something else has not gone wrong — but they
 * *have* just quietly tested whether this is a real game or a rail, and they deserve an answer.
 */
val STRAYED: Teaches = Teaches.Strayed

/** What the lesson has already said, so it does not say it twice. */
data class Taught(
    val talked: Set<String> = emptySet(),
    val notedRanks: Set<Rank> = emptySet(),
    val glossed: Set<String> = emptySet(),
    val chapters: Set<Chapter> = emptySet(),
) {
    fun withChapter(chapter: Chapter) = copy(chapters = chapters + chapter)

    /**
     * Everything a lesson said, marked off at once.
     *
     * Called when the player acknowledges a talk beat or simply makes their next move —
     * either way they have had it in front of them, and repeating it would be the coach not
     * listening.
     */
    fun heard(lesson: Lesson?): Taught {
        if (lesson == null) return this
        return copy(
            talked = lesson.talkId?.let { talked + it } ?: talked,
            notedRanks = lesson.noteRank?.let { notedRanks + it } ?: notedRanks,
            glossed = lesson.gloss?.let { glossed + it.id } ?: glossed,
            // A chapter is met when its lesson has been heard, not only when a move proves
            // it. Two of the nine — the call and the scoring — are taught in words over
            // things a bot does, and a player who reached the end of the lesson without
            // calling Vinto themselves used to finish with those two dots still empty.
            chapters = chapters + lesson.chapter,
        )
    }
}

/**
 * What to teach, given the table in front of the player.
 *
 * Derived from the position rather than from a step counter, which is the whole design. A
 * scripted walk has to be followed to work, and the first time a player does something else it
 * has to refuse them — which is the moment they learn this is not really the game. Reading the
 * position instead means every legal move stays legal: deviate, and the coach simply talks
 * about wherever you have got to, and the lesson it was about to give waits for the next
 * chance to give it.
 *
 * The talk beats are the exception, and they are ordered, because an explanation of scoring
 * before the round is scored is not an explanation.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
fun lessonFor(
    view: PlayerView,
    table: Table,
    taught: Taught,
    /**
     * What the player has seen of their own hand — position to card — as
     * `LocalGameSession.rememberedHand` reports it. Empty for a coach with no memory to
     * consult, which is every caller but the lesson screen; the swap advice then treats
     * every card as unseen, which is the honest reading.
     */
    memory: Map<Int, Card> = emptyMap(),
): Lesson? {
    talkFor(view, taught)?.let { return it }

    val you = view.players.first { it.id == view.viewerId }

    return when {
        view.phase == GamePhase.SCORING -> null

        view.phase == GamePhase.SETUP && table.taps.isNotEmpty() -> Lesson(
            chapter = Chapter.PEEK,
            teaches = Teaches.OnlyLook,
            point = Target.Place(Anchor.Seat(you.id, unknownOwn(view) ?: 0)),
            gloss = glossOnce(taught, Gloss.PULSE),
        )

        view.phase == GamePhase.SETUP -> Lesson(
            chapter = Chapter.PEEK,
            teaches = Teaches.PeeksEnd,
            point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
        )

        table.ranks.isNotEmpty() -> naming(table, memory)

        // Before the window it is offered in: the toss-in beat would otherwise say "throw
        // in a match" over the one moment the round has been built to reach.
        readyToCall(view, memory) && table.choices.any { it.label is Label.CallVinto } -> Lesson(
            chapter = Chapter.VINTO,
            teaches = Teaches.CallNow,
            point = Target.Button(Label.CallVinto),
        )

        tossWindow(table) -> Lesson(
            chapter = Chapter.TOSS,
            teaches = Teaches.TossIn(alsoThrewIn(view)),
            point = matchingOwnCard(view, memory)?.let { Target.Place(it) }
                ?: table.choices.firstOrNull()?.let { Target.Button(it.label) },
            gloss = glossOnce(taught, Gloss.TOSS),
        )

        // No pointer. The hand used to point at the box of recent moves for as long as the
        // gloss was unsaid, which on a phone is an arrow at a paragraph while three bots
        // move cards over it (product owner). The gloss says where to look; that is enough.
        table.waiting -> Lesson(
            chapter = Chapter.DRAW,
            teaches = watching(view),
            gloss = glossOnce(taught, Gloss.LOG),
        )

        else -> playing(view, table, taught, memory)
    }?.let { lesson ->
        val rank = visibleRanks(view).firstOrNull { it !in taught.notedRanks }
        lesson.copy(noteRank = rank)
    }
}

/** The moves of your own turn: take a card, then decide what to do with it. */
private fun playing(view: PlayerView, table: Table, taught: Taught, memory: Map<Int, Card>): Lesson? {
    val pending = (view.pendingAction?.card as? CardView.Visible)?.card

    val mine = view.pendingAction?.playerId == view.viewerId
    val aiming = view.pendingAction?.actionPhase == ActionPhase.SELECTING_TARGET

    return when {
        // Aiming a card's action, or deciding what it showed: the engine is mid-action.
        pending != null && mine && aiming -> acting(view, table, memory)

        // Choosing which of your own cards the drawn one replaces.
        pending != null && mine && table.taps.isNotEmpty() -> swapAdvice(view, table, memory, pending)

        pending != null && mine -> Lesson(
            chapter = Chapter.KEEP,
            teaches = Teaches.KeepOrThrow,
            point = keepOrThrow(view, table, memory, pending)?.let { Target.Button(it.label) },
        )

        // `is`, not `startsWith`. This is the beat the string comparison silently lost.
        table.choices.any { it.label is Label.UseFromPile } -> Lesson(
            chapter = Chapter.DRAW,
            teaches = Teaches.TwoWaysToStart,
            point = table.choices.firstOrNull { it.label is Label.UseFromPile }
                ?.let { Target.Button(it.label) },
        )

        table.choices.isNotEmpty() -> Lesson(
            chapter = Chapter.DRAW,
            teaches = Teaches.EveryTurnStarts,
            point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
            gloss = glossOnce(taught, Gloss.BADGE),
        )

        else -> null
    }
}

/**
 * The things to be read rather than done, in order.
 *
 * Each waits for a tap, and nothing on the table moves while one is up — which is the only
 * way to explain a card game to somebody who does not yet know what any of it means.
 */

/**
 * The things to be read rather than done, in order.
 *
 * Each waits for a tap, and nothing on the table moves while one is up — which is the only
 * way to explain a card game to somebody who does not yet know what any of it means.
 *
 * Three passes, in the order a person needs them: what the game is *for*, what the cards *do*,
 * and where everything *is*. The cards come before the table because a player looking at four
 * hands of face-down cards has no questions about the deck yet — they have one question, and
 * it is "what am I holding".
 */
private fun talkFor(view: PlayerView, taught: Taught): Lesson? = when {
    "welcome" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        teaches = Teaches.Welcome,
        talkId = "welcome",
    )

    // Said before a single card is explained, because it is the thing every card's
    // explanation assumes: you cannot see your own hand, so a card you have looked at is
    // worth more to you than its number says, and one you have not is worth less.
    "memory" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        teaches = Teaches.Memory,
        talkId = "memory",
    )

    else -> cardTour(taught) ?: tableTour(view, taught) ?: endgameTalk(view, taught)
}

/**
 * The talk beats before a card is dealt with, in the order they are said.
 *
 * The row of dots over the coach is the lesson's *chapters*, and during these fourteen beats
 * not one of them changes — the chapters are met by playing, and nothing has been played
 * yet — so a player pressing "Go on" thirteen times watched a progress row that did not move
 * (product owner). While the coach is in this run the dots count *these* instead, one per
 * beat; the chapters take over the moment the table is theirs.
 */
val INTRO_BEATS: List<String> = listOf(
    Teaches.Welcome, Teaches.Memory,
    Teaches.CardsNumbers, Teaches.CardsOwn, Teaches.CardsTheirs, Teaches.CardsJack, Teaches.CardsQueen,
    Teaches.CardsKing, Teaches.CardsKingName, Teaches.CardsKingWhose, Teaches.CardsOdd,
    Teaches.Tour, Teaches.Seats, Teaches.Help,
).map { it.id }

/** Which of the [INTRO_BEATS] a lesson is, or null for anything past them. */
fun introStep(lesson: Lesson?): Int? =
    lesson?.talkId?.let { INTRO_BEATS.indexOf(it) }?.takeIf { it >= 0 }

/**
 * The table with nothing on it that can be touched.
 *
 * Offered while the coach is talking. A talk beat holds the stage, so a move made during one
 * would be made against a table the player has not been shown the last moves of; and the
 * first thing a newcomer does with five breathing cards under a paragraph is tap one, which
 * used to work — the peek happened under the welcome (product owner). The prompt stays, so
 * the rail still says what is about to be asked.
 */
fun Table.heldStill(): Table = copy(
    choices = emptyList(),
    taps = emptyMap(),
    seats = emptyList(),
    ranks = emptyList(),
)

/**
 * Every card in the deck, held up, in the order they get harder.
 *
 * Plain cards, then the two that look at your own, then the two that look at somebody else's,
 * then the three that are genuinely difficult — the Jack that gambles, the Queen that does
 * not, and the King, which is the one nobody works out by watching.
 */
private fun cardTour(taught: Taught): Lesson? = when {
    "cards_numbers" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsNumbers,
        cards = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX),
        talkId = "cards_numbers",
    )

    "cards_own" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsOwn,
        cards = listOf(Rank.SEVEN, Rank.EIGHT),
        talkId = "cards_own",
    )

    "cards_theirs" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsTheirs,
        cards = listOf(Rank.NINE, Rank.TEN),
        talkId = "cards_theirs",
    )

    "cards_jack" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsJack,
        cards = listOf(Rank.JACK),
        talkId = "cards_jack",
    )

    "cards_queen" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsQueen,
        cards = listOf(Rank.QUEEN),
        talkId = "cards_queen",
    )

    "cards_king" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsKing,
        cards = listOf(Rank.KING),
        talkId = "cards_king",
    )

    "cards_king_name" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsKingName,
        cards = listOf(Rank.SEVEN, Rank.JACK, Rank.QUEEN),
        talkId = "cards_king_name",
    )

    "cards_king_whose" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsKingWhose,
        cards = listOf(Rank.KING, Rank.JOKER),
        talkId = "cards_king_whose",
    )

    "cards_odd" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.CardsOdd,
        cards = listOf(Rank.ACE, Rank.JOKER),
        talkId = "cards_odd",
    )

    else -> null
}

/** Where everything is, once the player knows what they are looking at. */
private fun tableTour(view: PlayerView, taught: Taught): Lesson? = when {
    "tour" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        teaches = Teaches.Tour,
        point = Target.Place(Anchor.Discard),
        talkId = "tour",
    )

    "seats" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        teaches = Teaches.Seats,
        point = Target.Seat(view.players.first { it.id != view.viewerId }.id),
        talkId = "seats",
    )

    "help" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        teaches = Teaches.Help,
        point = Target.Furniture(Target.HELP),
        talkId = "help",
    )

    else -> null
}

/** The end of the round, which is a different game from the one that came before it. */
private fun endgameTalk(view: PlayerView, taught: Taught): Lesson? = when {
    // The round is built for the learner to be the caller (`TeachingDeal`), so this is the
    // expected ending; the beats below it are for a learner who played on until a bot called.
    view.vintoCallerId == view.viewerId && "you_called" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        teaches = Teaches.YouCalled,
        talkId = "you_called",
    )

    view.vintoCallerId == view.viewerId && "coalition_vs_you" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        teaches = Teaches.CoalitionAgainstYou,
        point = view.players.firstOrNull { it.id != view.viewerId }?.let { Target.Seat(it.id) },
        talkId = "coalition_vs_you",
    )

    view.vintoCallerId == view.viewerId -> scoringTalk(view, taught)

    view.vintoCallerId != null && "vinto" !in taught.talked -> vintoTalk(view)

    view.vintoCallerId != null && "coalition" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        teaches = Teaches.Coalition,
        point = Target.Seat(view.vintoCallerId!!),
        talkId = "coalition",
    )

    view.vintoCallerId != null && "your_turn_to_call" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        teaches = Teaches.YourTurnToCall,
        talkId = "your_turn_to_call",
    )

    else -> scoringTalk(view, taught)
}

/** The two beats over the face-up hands, whoever called. */
private fun scoringTalk(view: PlayerView, taught: Taught): Lesson? = when {
    view.phase == GamePhase.SCORING && "scoring" in taught.talked &&
        "session" !in taught.talked -> Lesson(
        chapter = Chapter.SCORE,
        teaches = Teaches.Session,
        talkId = "session",
    )

    view.phase == GamePhase.SCORING && "scoring" !in taught.talked -> Lesson(
        chapter = Chapter.SCORE,
        teaches = Teaches.Scoring,
        talkId = "scoring",
    )

    else -> null
}

/**
 * A card's action under way: aiming it, looking at what it found, or deciding on a trade.
 *
 * Its own function because these three are one phase of the engine's — `SELECTING_TARGET`
 * — told apart by what the table offers: cards to tap while a target is wanted, the two
 * buttons of a Queen that has seen both its cards, and "put it down" over a peek.
 */
private fun acting(view: PlayerView, table: Table, memory: Map<Int, Card>): Lesson = when {
    table.taps.isNotEmpty() -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.AimIt,
        point = worthLookingAt(view, table, memory),
    )

    // A Queen that has looked at both its cards, asking whether to trade them.
    view.pendingAction?.targetType == TargetType.PEEK_THEN_SWAP -> queenDecision(view, table)

    // A peek turned face up, waiting to be put down. It used to fall through to the
    // keep-or-throw beat, whose words are about a drawn card.
    table.choices.any { it.label is Label.PutItDown } -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.RememberIt,
        point = Target.Button(Label.PutItDown),
    )

    // Anything else mid-action — a Jack with both cards chosen, say — is answered by its
    // first button, so the coach never goes quiet with a card in play.
    else -> Lesson(
        chapter = Chapter.ACTIONS,
        teaches = Teaches.AimIt,
        point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
    )
}

/**
 * Somebody else's turn, and what they are doing with it.
 *
 * Named when a bot has an action card engaged: the one line the shut coach shows then says
 * "Mikey plays the 9 — Peek at one card of another player" as the 9 is being played, which
 * is how the learner meets the cards the round never puts in their hand. A bot merely
 * deciding about a drawn card is the plain beat, with no heading, as before.
 */
private fun watching(view: PlayerView): Teaches.Watching {
    val pending = view.pendingAction ?: return Teaches.Watching()
    if (pending.playerId == view.viewerId || pending.actionPhase != ActionPhase.SELECTING_TARGET) {
        return Teaches.Watching()
    }
    val card = (pending.card as? CardView.Visible)?.card ?: return Teaches.Watching()
    val who = view.players.firstOrNull { it.id == pending.playerId }?.nickname ?: return Teaches.Watching()
    return Teaches.Watching(Speaker.Named(who), card.rank)
}

/**
 * The card going down is being asked about: name it if you have seen it, and only then.
 *
 * The rank is read from what the player *remembers* of the slot the swap is about to empty,
 * never from the view — after the setup peeks the view shows nothing, which is the same bug
 * the swap advice had, and why the 7 went down unnamed in the first round anybody played
 * (product owner). A slot they never looked at is a guess, and the beat says not to.
 */
private fun naming(table: Table, memory: Map<Int, Card>): Lesson {
    val rank = declarableRank(table, memory)
    return if (rank != null) {
        Lesson(chapter = Chapter.DECLARE, teaches = Teaches.NameOnlySeen, point = Target.Chip(rank))
    } else {
        Lesson(
            chapter = Chapter.DECLARE,
            teaches = Teaches.DoNotGuess,
            point = table.choices.firstOrNull { it.label is Label.JustSwap }?.let { Target.Button(it.label) },
        )
    }
}

/**
 * Both cards looked at: trade when theirs is worth less than yours.
 *
 * The Queen shows its holder both cards, so this is one of the few places the coach can
 * compare real values rather than remembered ones. `view.pendingAction.targets` carries the
 * faces for the seat that played it.
 */
private fun queenDecision(view: PlayerView, table: Table): Lesson {
    val targets = view.pendingAction?.targets.orEmpty()
    val mine = targets.firstOrNull { it.playerId == view.viewerId }?.card as? CardView.Visible
    val theirs = targets.firstOrNull { it.playerId != view.viewerId }?.card as? CardView.Visible
    val trade = mine != null && theirs != null && theirs.card.value < mine.card.value
    val wanted = if (trade) Label.SwapCards else Label.LeaveThem
    return Lesson(
        chapter = Chapter.ACTIONS,
        teaches = if (trade) Teaches.SwapThem else Teaches.LeaveThem,
        point = table.choices.firstOrNull { it.label == wanted }?.let { Target.Button(it.label) },
    )
}

/**
 * Whether the hand is one to call Vinto on: every card in it seen, and the total at or
 * below zero. The taught round reaches exactly this at the end of the learner's second turn
 * — two Jokers and two Kings — and the coach then points at the gold button.
 */
fun readyToCall(view: PlayerView, memory: Map<Int, Card>): Boolean {
    val hand = view.players.firstOrNull { it.id == view.viewerId }?.cards ?: return false
    if (hand.isEmpty() || hand.indices.any { it !in memory }) return false
    return memory.values.sumOf { it.value } <= 0
}

private fun vintoTalk(view: PlayerView): Lesson {
    // A `Speaker` rather than a name, so the renderer decides how a person is addressed — and
    // so the fallback for a caller the view cannot name is a translated word rather than the
    // literal English "Somebody" this used to interpolate.
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId }
        ?.let { Speaker.Named(it.nickname) } ?: Speaker.Nobody
    return Lesson(
        chapter = Chapter.VINTO,
        teaches = Teaches.VintoCalled(caller),
        point = Target.Seat(view.vintoCallerId!!),
        talkId = "vinto",
    )
}

/** Every rank the player can currently see: their own known cards, the pile, the card in play. */
internal fun visibleRanks(view: PlayerView): List<Rank> {
    val mine = view.players.first { it.id == view.viewerId }.cards
        .mapNotNull { (it as? CardView.Visible)?.card?.rank }
    val pending = ((view.pendingAction?.card as? CardView.Visible)?.card?.rank)?.let(::listOf).orEmpty()
    val pile = view.discardTop?.rank?.let(::listOf).orEmpty()
    return mine + pending + pile
}

/** A gloss is said the first time its thing appears on the table, and never again. */
private fun glossOnce(taught: Taught, gloss: Gloss): Gloss? =
    gloss.takeIf { it.id !in taught.glossed }

/**
 * Where to aim an action, when the coach can tell.
 *
 * A peek spent on a card you looked at a minute ago buys nothing, so for your own cards it
 * points at one you have *not* seen. For anybody else's it takes the first on offer — every
 * card in an opponent's hand is equally unknown, which is the whole problem with them.
 */
private fun worthLookingAt(view: PlayerView, table: Table, memory: Map<Int, Card>): Target? {
    val me = view.viewerId

    // Unseen by the player's own memory, not by the view — the view shows none of your cards
    // once the setup peeks are over, so read from it every slot looked equally unread.
    val unseen = table.taps.keys.firstOrNull { ref -> ref.playerId == me && ref.position !in memory }
    val chosen = unseen ?: table.taps.keys.firstOrNull() ?: return null

    return Target.Place(Anchor.Seat(chosen.playerId, chosen.position))
}

/**
 * Which of your own cards the drawn one should replace, and the lesson that goes with it.
 *
 * Three answers, in order. **A card you know is worse than this one**: give up the highest
 * of those, which is the trade that loses points for certain — and if it has an action, the
 * declaration beat that follows will have you name it. **Nothing you know is worse**: then
 * trading a card you know for one you know is worth *more* is throwing knowledge away, so
 * the slot to take is one you have never looked at, provided the drawn card is a fair one —
 * the deck averages about five and a half, so up to a 5 an unseen card is more likely worse
 * than better, and either way you know one more of your own cards afterwards. **Neither**:
 * go back and throw it away.
 *
 * It used to point at the highest card the *view* showed face up, which after the setup
 * peeks is none of them — the view hides what you have seen, on purpose — so every value
 * was the same and it pointed at card one whatever it was. Reported with the exact hand: a
 * 3 and a 7 known, a 4 drawn, and the coach pointing at the 3.
 */
private fun swapAdvice(view: PlayerView, table: Table, memory: Map<Int, Card>, drawn: Card): Lesson {
    val you = view.viewerId
    val mine = table.taps.keys.filter { it.playerId == you }

    val worse = mine
        .filter { memory[it.position]?.value ?: Int.MIN_VALUE > drawn.value }
        .maxByOrNull { memory.getValue(it.position).value }
    if (worse != null) {
        return Lesson(
            chapter = Chapter.KEEP,
            teaches = Teaches.GiveUpWorst,
            point = Target.Place(Anchor.Seat(you, worse.position)),
        )
    }

    val unseen = mine.firstOrNull { it.position !in memory }
    if (unseen != null && drawn.value <= BLIND_SWAP_UP_TO) {
        return Lesson(
            chapter = Chapter.KEEP,
            teaches = Teaches.SwapBlind,
            point = Target.Place(Anchor.Seat(you, unseen.position)),
        )
    }

    return Lesson(
        chapter = Chapter.KEEP,
        teaches = Teaches.NothingWorse,
        point = table.choices.firstOrNull { it.label is Label.Back }?.let { Target.Button(it.label) },
    )
}

/**
 * Which button to press with a drawn card in hand: its action if it has one — the lesson's
 * deal puts a Queen and a King in the player's way for exactly that — otherwise swap when
 * [swapAdvice] would have somewhere to put it, and throw it away when it would not.
 */
private fun keepOrThrow(view: PlayerView, table: Table, memory: Map<Int, Card>, drawn: Card): Choice? {
    table.choices.firstOrNull { it.label is Label.UseAction }?.let { return it }

    val hand = view.players.first { it.id == view.viewerId }.cards.indices
    val knownWorse = hand.any { memory[it]?.value ?: Int.MIN_VALUE > drawn.value }
    val blindIsFair = hand.any { it !in memory } && drawn.value <= BLIND_SWAP_UP_TO
    val wanted = if (knownWorse || blindIsFair) Label.SwapCards else Label.Discard

    return table.choices.firstOrNull { it.label == wanted } ?: table.choices.firstOrNull()
}

/**
 * The highest drawn card worth swapping blind for one you have never seen.
 *
 * The deck's average card is worth about five and a half — fifty-four cards summing to 298 —
 * so a 5 is more likely than not to beat whatever is under an unread slot, and a 6 is not.
 */
private const val BLIND_SWAP_UP_TO = 5

/** Which of the player's own cards they have not seen yet — the one worth spending a peek on. */
private fun unknownOwn(view: PlayerView): Int? = view.players
    .first { it.id == view.viewerId }
    .cards
    .indexOfFirst { it !is CardView.Visible }
    .takeIf { it >= 0 }

/**
 * The rank the player can safely name, if any.
 *
 * The card has not been put down yet — the whole declaration is a question the screen asks
 * *before* dispatching the swap — so the rank cannot be read off the pile. It is read from the
 * slot the swap is about to empty, and only when the player has actually seen that card. A
 * coach that pointed at a rank the player had not peeked would be teaching them to guess,
 * which is the one thing this move punishes.
 */
private fun declarableRank(table: Table, memory: Map<Int, Card>): Rank? {
    val position = table.ranks.firstNotNullOfOrNull { choice ->
        ((choice.move as? Move.Send)?.action as? GameAction.SwapCard)?.payload?.position
    } ?: return null

    return memory[position]?.rank
}

/**
 * A card of the player's own that matches the open toss-in window, if they remember one —
 * and only one worth being rid of. A King costs nothing to hold, and throwing one in buys a
 * declaration the learner then has to make; the coach does not send them there.
 */
private fun matchingOwnCard(view: PlayerView, memory: Map<Int, Card>): Anchor? {
    val wanted = view.activeTossIn?.ranks?.toSet() ?: return null
    val match = memory.entries.firstOrNull { (_, card) -> card.rank in wanted && card.value > 0 }
    return match?.let { Anchor.Seat(view.viewerId, it.key) }
}

/**
 * Whoever else has already thrown into this window, named.
 *
 * Watching an opponent do it is the difference between "a prompt I dismiss" and "a thing the
 * whole table does at once" — so when it happens, the lesson says whose card that was.
 *
 * Names rather than a sentence: joining them with " and " is a grammar decision, and it is not
 * the same decision in every language.
 */
private fun alsoThrewIn(view: PlayerView): List<String> {
    val others = view.activeTossIn?.participants.orEmpty().filter { it != view.viewerId }
    return others.mapNotNull { id -> view.players.firstOrNull { it.id == id }?.nickname }
}

/**
 * Whether the table is in a toss-in window.
 *
 * Three ways to be in one, and the third used to be `label.contains("Pass")` — a second dead
 * English match, since no button has said "Pass" for some time. What it meant is the button
 * that declares you finished with the window, so it asks for that action instead. Same
 * intent, and a rewording cannot silently remove it.
 */
private fun tossWindow(table: Table): Boolean =
    table.choices.any { (it.move as? Move.Send)?.action is GameAction.ParticipateInTossIn } ||
        table.taps.values.any { (it as? Move.Send)?.action is GameAction.ParticipateInTossIn } ||
        table.choices.any { (it.move as? Move.Send)?.action is GameAction.PlayerTossInFinished }

/** Which chapter a move is proof of, for the row of dots. */
fun chapterOf(action: GameAction): Chapter? = when (action) {
    is GameAction.PeekSetupCard -> Chapter.PEEK
    is GameAction.DrawCard, is GameAction.PlayDiscard -> Chapter.DRAW
    // A swap that names a rank is the declaration lesson; one that does not is the keep-or-
    // throw lesson. Same action, and which of the two it teaches is in its payload.
    is GameAction.SwapCard ->
        if (action.payload.declaredRank != null) Chapter.DECLARE else Chapter.KEEP
    is GameAction.DiscardCard -> Chapter.KEEP
    is GameAction.UseCardAction, is GameAction.SelectActionTarget,
    is GameAction.DeclareKingAction,
    -> Chapter.ACTIONS
    is GameAction.ParticipateInTossIn -> Chapter.TOSS
    is GameAction.CallVinto -> Chapter.VINTO
    else -> null
}
