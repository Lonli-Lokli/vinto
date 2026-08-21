package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig

/**
 * The nine things there are to learn, in the order `VINTO_RULES.md` puts them.
 *
 * One per heading of the rules rather than one per mechanic, because that is the shape a
 * player will meet the game in again if they ever read them: the table, the deal, taking a
 * card, keeping or throwing it, naming a rank, what the cards do, throwing in, calling Vinto,
 * and how the round is scored.
 */
enum class Chapter(val label: String) {
    TABLE("The table"),
    PEEK("Your two peeks"),
    DRAW("Taking a card"),
    KEEP("Keep or throw"),
    DECLARE("Naming a rank"),
    ACTIONS("What the cards do"),
    TOSS("Throwing in"),
    VINTO("Calling Vinto"),
    SCORE("Scoring"),
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
    data class Button(val label: String) : Target
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
     * What this beat is *about* — never what to do about it.
     *
     * The control panel underneath already gives the instruction ("Look at two of your
     * cards"), and a coach that says the same thing in its own words has spent the top line of
     * the rail on a sentence the player has already read. So the title is the reason, the
     * consequence, or the thing nobody would guess: "The only look you get".
     */
    val title: String?,
    val body: String,
    val point: Target? = null,
    /**
     * Set when the lesson is something to *read* rather than something to do: the screen shows
     * a Continue button and nothing moves until it is pressed. A card game explained at the
     * speed of a card game is not explained.
     */
    val talkId: String? = null,
    /**
     * Cards to hold up while this is being said.
     *
     * A rank explained in words is a rank the player then has to match against a picture on
     * the felt. The lesson shows the cards it is talking about.
     */
    val cards: List<Rank> = emptyList(),
    /** A card the player is meeting for the first time, said in the game's own words. */
    val note: String? = null,
    val noteRank: Rank? = null,
    /** What a glow or a ring on the table means, said once, the first time it appears. */
    val gloss: String? = null,
    val glossId: String? = null,
)

/**
 * What the coach says the first time somebody ignores it.
 *
 * Once, without reproach, and never again. The pointer is a suggestion and the lesson is
 * derived from the position, so a player who does something else has not gone wrong — but they
 * *have* just quietly tested whether this is a real game or a rail, and they deserve an answer.
 */
const val STRAYED = "Your table, not mine — nothing here is a wrong move. " +
    "The lesson picks up wherever you take it."

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
            glossed = lesson.glossId?.takeIf { lesson.gloss != null }?.let { glossed + it } ?: glossed,
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
fun lessonFor(view: PlayerView, table: Table, taught: Taught): Lesson? {
    talkFor(view, taught)?.let { return it }

    val you = view.players.first { it.id == view.viewerId }

    return when {
        view.phase == GamePhase.SCORING -> null

        view.phase == GamePhase.SETUP && table.taps.isNotEmpty() -> Lesson(
            chapter = Chapter.PEEK,
            title = "The only look you get",
            body = "Everybody may look at two of their own cards before the round starts, once. " +
                "From here on the game is memory: yours against three other people's.",
            point = Target.Place(Anchor.Seat(you.id, unknownOwn(view) ?: 0)),
            glossId = "pulse",
            gloss = glossOnce(taught, "pulse", "A card that breathes can be touched right now."),
        )

        view.phase == GamePhase.SETUP -> Lesson(
            chapter = Chapter.PEEK,
            title = null,
            body = "They turn face down again — remember where they were. Everybody else has " +
                "peeked at two of theirs as well.",
            point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
        )

        table.ranks.isNotEmpty() -> Lesson(
            chapter = Chapter.DECLARE,
            title = "Only name one you have seen",
            body = "You looked at this card, so you know what it is. Name it right and you play " +
                "its action for free. Name it wrong and you take a penalty card — so only " +
                "name one you are sure of.",
            point = declarableRank(view, table)?.let { Target.Chip(it) },
        )

        tossWindow(table) -> Lesson(
            chapter = Chapter.TOSS,
            title = "Anybody holding that rank may throw it in",
            body = alsoThrewIn(view) + "The moment a card lands face up, everyone gets a " +
                "chance to be rid of a match — and to use its action. Wrong rank costs you a " +
                "penalty card and bars you from throwing in for the rest of the round.",
            point = matchingOwnCard(view)?.let { Target.Place(it) }
                ?: table.choices.firstOrNull()?.let { Target.Button(it.label) },
            glossId = "toss",
            gloss = glossOnce(
                taught,
                "toss",
                "The chip under the piles names the rank the window is open for.",
            ),
        )

        table.waiting -> Lesson(
            chapter = Chapter.DRAW,
            title = null,
            body = "Watch what they take and what they put down. That, and the line-by-line " +
                "under here, is everything you get to know about their hands.",
            point = Target.Furniture(Target.LOG).takeIf { "log" !in taught.glossed },
            glossId = "log",
            gloss = glossOnce(
                taught,
                "log",
                "Every move is written down in this box — glance at it when cards moved " +
                    "faster than you could read.",
            ),
        )

        else -> playing(view, table, taught)
    }?.let { lesson ->
        val rank = visibleRanks(view).firstOrNull { it !in taught.notedRanks }
        lesson.copy(note = rank?.let(::noteFor), noteRank = rank)
    }
}

/** The moves of your own turn: take a card, then decide what to do with it. */
private fun playing(view: PlayerView, table: Table, taught: Taught): Lesson? {
    val pending = (view.pendingAction?.card as? CardView.Visible)?.card

    val mine = view.pendingAction?.playerId == view.viewerId
    val aiming = view.pendingAction?.actionPhase == ActionPhase.SELECTING_TARGET

    return when {
        // Aiming a card's action at somebody: the engine is waiting for a target.
        pending != null && mine && aiming && table.taps.isNotEmpty() -> Lesson(
            chapter = Chapter.ACTIONS,
            title = "Aim it",
            body = "This is the card's own action, and it is free — it costs you nothing to " +
                "use, and the card goes on the pile afterwards either way.",
            point = worthLookingAt(view, table),
        )

        // Choosing which of your own cards the drawn one replaces.
        pending != null && mine && table.taps.isNotEmpty() -> Lesson(
            chapter = Chapter.KEEP,
            title = "Give up your worst card",
            body = "The new card goes in face down, and the one it replaces goes face up on " +
                "the pile for everybody to read. Give up the card you least want to be " +
                "holding — the highest one you know about.",
            point = worstKnown(view, table)?.let { Target.Place(it) },
        )

        pending != null && view.pendingAction?.playerId == view.viewerId -> Lesson(
            chapter = Chapter.KEEP,
            title = "Keep it or throw it",
            body = "Put it in your hand — face down, in place of a card you own, which goes " +
                "face up on the pile — or throw it away. Every card in your hand counts " +
                "against you, so a low one is worth keeping. If it has an action, playing it " +
                "now instead is the third choice, and it costs you nothing to take.",
            point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
        )

        table.choices.any { it.label.startsWith("Take the") } -> Lesson(
            chapter = Chapter.DRAW,
            title = "Two ways to start a turn",
            body = "From the deck, sight unseen — or off the pile, but only an action card " +
                "nobody has used, and then you must play it at once rather than keep it.",
            point = table.choices.firstOrNull { it.label.startsWith("Take the") }
                ?.let { Target.Button(it.label) },
        )

        table.choices.isNotEmpty() -> Lesson(
            chapter = Chapter.DRAW,
            title = "Every turn starts the same way",
            body = "A turn begins by taking a card. The deck is face down, so what you get is " +
                "a surprise — that is the risk you are being paid for.",
            point = table.choices.firstOrNull()?.let { Target.Button(it.label) },
            glossId = "badge",
            gloss = glossOnce(taught, "badge", "The green number counts what is left in the deck."),
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
        title = "Four players, five cards each",
        body = "Every card counts against you and the lowest hand wins. This is a real round " +
            "against three real opponents — the coach only walks beside you.",
        talkId = "welcome",
    )

    else -> cardTour(taught) ?: tableTour(view, taught) ?: endgameTalk(view, taught)
}

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
        title = "2 to 6 — plain cards",
        body = "Worth exactly what they say and nothing else. Small ones are what a winning " +
            "hand is made of.",
        cards = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX),
        talkId = "cards_numbers",
    )

    "cards_own" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "7 and 8 — look at one of yours",
        body = "Worth 7 and 8, which is a lot to be holding. What you get for it is a look at " +
            "one of your own cards — and this game is memory, so a card you have seen is " +
            "worth more to you than a card you have not.",
        cards = listOf(Rank.SEVEN, Rank.EIGHT),
        talkId = "cards_own",
    )

    "cards_theirs" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "9 and 10 — look at one of theirs",
        body = "The same look, pointed the other way: one card in somebody else's hand. " +
            "Remember where it was. Two turns later it is what tells you whether to call.",
        cards = listOf(Rank.NINE, Rank.TEN),
        talkId = "cards_theirs",
    )

    "cards_jack" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "Jack — swap two cards, blind",
        body = "Worth 10. It swaps two cards belonging to two different players — never two " +
            "of anybody's own — and nobody looks at either of them first. Good for pushing a " +
            "card you know is bad into somebody else's hand; a gamble with anything you know " +
            "is good.",
        cards = listOf(Rank.JACK),
        talkId = "cards_jack",
    )

    "cards_queen" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "Queen — look at two, then decide",
        body = "Also worth 10, and the strongest card in the game: look at any two cards from " +
            "two different players, and only then choose whether to swap them. The Jack " +
            "gambles. The Queen knows.",
        cards = listOf(Rank.QUEEN),
        talkId = "cards_queen",
    )

    "cards_king" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "King — worth nothing, and does anything",
        body = "Worth nothing at all, which alone makes it worth keeping. Its action points at " +
            "any card on the table — yours or anybody's — and names it. Right: that card " +
            "leaves the hand it was in, and its action becomes yours to play. Wrong: everybody " +
            "sees what it really was, and you take a penalty card.",
        cards = listOf(Rank.KING),
        talkId = "cards_king",
    )

    "cards_king_name" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "What you name is what you get to play",
        body = "So you are really choosing an action, out of the cards you have seen. Name a 7 " +
            "or an 8 and you get a look at one of your own. Name a Jack and you get its blind " +
            "swap — and you pick both cards, which is how a Joker you have spotted comes to " +
            "you and your worst card goes the other way. Name a Queen and you look at two " +
            "before deciding whether to trade.",
        cards = listOf(Rank.SEVEN, Rank.JACK, Rank.QUEEN),
        talkId = "cards_king_name",
    )

    "cards_king_whose" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "And whose card you name",
        body = "Whoever was holding it, the card you name leaves their hand. Naming your own " +
            "10 takes ten points off your total; naming Don's Joker takes his minus one away " +
            "and leaves him a card worse off. One King, two jobs — which of them matters more " +
            "is the read.",
        cards = listOf(Rank.KING, Rank.JOKER),
        talkId = "cards_king_whose",
    )

    "cards_odd" !in taught.talked -> Lesson(
        chapter = Chapter.ACTIONS,
        title = "Ace and Joker",
        body = "An Ace is worth 1 and makes whoever you choose draw a penalty card. A Joker is " +
            "worth minus one — the best card in the deck, and the one never to give away.",
        cards = listOf(Rank.ACE, Rank.JOKER),
        talkId = "cards_odd",
    )

    else -> null
}

/** Where everything is, once the player knows what they are looking at. */
private fun tableTour(view: PlayerView, taught: Taught): Lesson? = when {
    "tour" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        title = "The deck and the pile",
        body = "Cards come off the deck. What anybody throws away lands face up beside it, " +
            "where the whole table can read it — including you.",
        point = Target.Place(Anchor.Discard),
        talkId = "tour",
    )

    "seats" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        title = "Raph, Mikey and Don",
        body = "Three bots, each with five cards they can see no better than you can. The " +
            "plate that lights up green is whoever's turn it is.",
        point = Target.Seat(view.players.first { it.id != view.viewerId }.id),
        talkId = "seats",
    )

    "help" !in taught.talked -> Lesson(
        chapter = Chapter.TABLE,
        title = "The ? is always there",
        body = "It explains the moment you are in and what every card does. The boxed game " +
            "comes with reminder cards for exactly this, one per player — nobody is expected " +
            "to hold fourteen ranks in their head, here or at a table.",
        point = Target.Furniture(Target.HELP),
        talkId = "help",
    )

    else -> null
}

/** The end of the round, which is a different game from the one that came before it. */
private fun endgameTalk(view: PlayerView, taught: Taught): Lesson? = when {
    view.vintoCallerId != null && "vinto" !in taught.talked -> vintoTalk(view)

    view.vintoCallerId != null && "coalition" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        title = "Everybody else plays as one",
        body = "You, and the two who did not call, are the coalition: one turn each, and only " +
            "your single best hand is compared with the caller's — so it is a team against " +
            "one hand. At a real table you would talk it over and pool what you know. Nobody " +
            "may touch the caller's cards, and the game will not let you try.",
        point = Target.Seat(view.vintoCallerId!!),
        talkId = "coalition",
    )

    view.vintoCallerId != null && "your_turn_to_call" !in taught.talked -> Lesson(
        chapter = Chapter.VINTO,
        title = "You can call it too",
        body = "At the end of any turn of yours, the gold button is there: press it when you " +
            "believe your hand is the lowest at the table. It was hidden while you were " +
            "learning because it ends the round for everybody — from here on it is yours to " +
            "press, and pressing it early is how most people lose their first game.",
        talkId = "your_turn_to_call",
    )

    view.phase == GamePhase.SCORING && "scoring" in taught.talked &&
        "session" !in taught.talked -> Lesson(
        chapter = Chapter.SCORE,
        title = "And that is one round",
        body = "A game is rounds, one after another, with those points carried between them, " +
            "played to a clock somebody agrees beforehand — half an hour is usual. When the " +
            "time is up the round in progress is finished, and whoever has the most points " +
            "comes first for 5 game points, second for 3, third for 2. A round won by three " +
            "counts the same whether you won it by one point or by twenty.",
        talkId = "session",
    )

    view.phase == GamePhase.SCORING && "scoring" !in taught.talked -> Lesson(
        chapter = Chapter.SCORE,
        title = "Every hand goes face up",
        body = "The caller's hand is set against the best hand among everybody else. Lower, " +
            "and the caller takes +3 while everybody else loses 1 each. Level counts as the " +
            "caller's: +3 to them, nothing lost by anyone. Beaten, and the caller loses 1 " +
            "while every one of the others takes +3.",
        talkId = "scoring",
    )

    else -> null
}

private fun vintoTalk(view: PlayerView): Lesson {
    val caller = view.players.firstOrNull { it.id == view.vintoCallerId }?.nickname ?: "Somebody"
    return Lesson(
        chapter = Chapter.VINTO,
        title = "$caller called Vinto",
        body = "That is the bet that their hand is the lowest at the table. It ends the round: " +
            "everybody else gets exactly one more turn, and then every hand is turned over.",
        point = Target.Seat(view.vintoCallerId!!),
        talkId = "vinto",
    )
}

/**
 * A card the player is seeing for the first time, in the game's own words.
 *
 * The words are `CARD_CONFIGS` — the same copy the help sheet and the web app show — so the
 * lesson cannot teach a rule the rest of the game does not have.
 */
private fun noteFor(rank: Rank): String {
    val config = getCardConfig(rank)
    val worth = "${config.name} — worth ${config.value}"

    return if (config.longDescription.isEmpty()) {
        "$worth, and it does nothing at all. A hand full of small ones of these is a good hand."
    } else {
        "$worth. ${config.longDescription}."
    }
}

/** Every rank the player can currently see: their own known cards, the pile, the card in play. */
internal fun visibleRanks(view: PlayerView): List<Rank> {
    val mine = view.players.first { it.id == view.viewerId }.cards
        .mapNotNull { (it as? CardView.Visible)?.card?.rank }
    val pending = ((view.pendingAction?.card as? CardView.Visible)?.card?.rank)?.let(::listOf).orEmpty()
    val pile = view.discardPile.lastOrNull()?.rank?.let(::listOf).orEmpty()
    return mine + pending + pile
}

private fun glossOnce(taught: Taught, id: String, line: String): String? =
    line.takeIf { id !in taught.glossed }

/**
 * Where to aim an action, when the coach can tell.
 *
 * A peek spent on a card you looked at a minute ago buys nothing, so for your own cards it
 * points at one you have *not* seen. For anybody else's it takes the first on offer — every
 * card in an opponent's hand is equally unknown, which is the whole problem with them.
 */
private fun worthLookingAt(view: PlayerView, table: Table): Target? {
    val mine = view.players.first { it.id == view.viewerId }
    val hand = mine.cards

    val unseen = table.taps.keys.firstOrNull { ref ->
        ref.playerId == mine.id && hand.getOrNull(ref.position) !is CardView.Visible
    }
    val chosen = unseen ?: table.taps.keys.firstOrNull() ?: return null

    return Target.Place(Anchor.Seat(chosen.playerId, chosen.position))
}

/**
 * The card the player should most want rid of: the highest one they can actually see.
 *
 * Pointing at a card they know nothing about would be advice the coach cannot justify — the
 * whole point of the lesson is that you play what you remember.
 */
private fun worstKnown(view: PlayerView, table: Table): Anchor? {
    val you = view.viewerId
    val mine = table.taps.keys.filter { it.playerId == you }
    if (mine.isEmpty()) return null

    val hand = view.players.first { it.id == you }.cards
    val best = mine.maxByOrNull { ref ->
        (hand.getOrNull(ref.position) as? CardView.Visible)?.card?.value ?: Int.MIN_VALUE
    } ?: return null

    return Anchor.Seat(best.playerId, best.position)
}

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
private fun declarableRank(view: PlayerView, table: Table): Rank? {
    val position = table.ranks.firstNotNullOfOrNull { choice ->
        ((choice.move as? Move.Send)?.action as? GameAction.SwapCard)?.payload?.position
    } ?: return null

    val card = view.players.first { it.id == view.viewerId }.cards.getOrNull(position)
    return (card as? CardView.Visible)?.card?.rank
}

/** A card of the player's own that matches the open toss-in window, if they can see one. */
private fun matchingOwnCard(view: PlayerView): Anchor? {
    val wanted = view.activeTossIn?.ranks?.toSet() ?: return null
    val you = view.players.first { it.id == view.viewerId }

    val position = you.cards.indexOfFirst { card ->
        (card as? CardView.Visible)?.card?.rank in wanted
    }
    return position.takeIf { it >= 0 }?.let { Anchor.Seat(you.id, it) }
}

/**
 * Whoever else has already thrown into this window, named.
 *
 * Watching an opponent do it is the difference between "a prompt I dismiss" and "a thing the
 * whole table does at once" — so when it happens, the lesson says whose card that was.
 */
private fun alsoThrewIn(view: PlayerView): String {
    val others = view.activeTossIn?.participants.orEmpty().filter { it != view.viewerId }
    val names = others.mapNotNull { id -> view.players.firstOrNull { it.id == id }?.nickname }

    return if (names.isEmpty()) "" else "${names.joinToString(" and ")} just threw one in. "
}

private fun tossWindow(table: Table): Boolean =
    table.choices.any { (it.move as? Move.Send)?.action is GameAction.ParticipateInTossIn } ||
        table.taps.values.any { (it as? Move.Send)?.action is GameAction.ParticipateInTossIn } ||
        table.choices.any { it.label.contains("Pass", ignoreCase = true) }

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
