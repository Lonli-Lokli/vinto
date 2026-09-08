package game.vinto.client

import game.vinto.shapes.Rank
import game.vinto.shapes.TableTalk

/**
 * Something the table has to say, as a *message* rather than as a sentence.
 *
 * This is the first slice of the change WORDS.md §6h describes. `shared/client` has no Compose and no
 * resources, and until now it answered questions like "what just happened" with an English
 * string — so the menus and the settings followed the phone's language and the table did not.
 *
 * A string table alone cannot fix that, which is the whole reason for a type here. The move
 * log conjugates: "You draw" against "Raph draws". In English that is a suffix; in Belarusian
 * or Ukrainian it is a different sentence, and a translator handed the fragment `"draws"`
 * cannot fix a half they were never given. So this module says *what happened* and the UI
 * says it in words — which is also why the tests get better rather than worse, because
 * `Say.Drew(You)` states what is meant where an English sentence only states what it reads.
 *
 * Ranks travel as [Rank] and are rendered as their symbol (`7`, `K`, `A`). A rank is a mark
 * on a card rather than a word, so it is the one thing here that does not need translating.
 */
sealed interface Say {

    /** Who did it. The one genuinely variable part of any of these sentences. */
    val who: Speaker

    /**
     * A card drawn, seen by the person who drew it.
     *
     * Only ever [Speaker.You] — nobody else is shown what came off the deck — but the speaker
     * is carried anyway rather than assumed, so a renderer never has to know that rule.
     */
    data class DrewKnown(override val who: Speaker, val rank: Rank) : Say

    /** A card drawn, as everybody else sees it. */
    data class Drew(override val who: Speaker) : Say

    /** Taken off the discard pile. Null when the pile's top was not visible. */
    data class Took(override val who: Speaker, val rank: Rank?) : Say

    /**
     * A card swapped into a hand.
     *
     * [slot] is one-based, because it is read by a person counting cards along a row.
     * [dropped] is what came out, when that was visible.
     */
    data class Swapped(override val who: Speaker, val slot: Int, val dropped: Rank?) : Say

    data class ThrewAway(override val who: Speaker, val rank: Rank?) : Say

    data class Played(override val who: Speaker, val rank: Rank?) : Say

    /**
     * A toss-in, with the rank rather than a count.
     *
     * "Mikey tossed in 1 card" says somebody acted and nothing about the game; "Mikey tossed
     * in a 6" is a card leaving a hand the player is trying to remember.
     */
    data class TossedIn(override val who: Speaker, val rank: Rank?) : Say

    /**
     * A toss-in that guessed wrong: the card stays, and a penalty card arrives.
     *
     * Its own message rather than [TossedIn], because the old line reported a failed throw as
     * a successful one — and with the *pile's* rank, since the thrown card never got there.
     * [rank] is the card actually tried, which the whole table saw turned over.
     */
    data class TossInMissed(override val who: Speaker, val rank: Rank?) : Say

    data class CalledVinto(override val who: Speaker) : Say

    /**
     * A Jack or Queen's swap, with what it moved.
     *
     * "Mikey swapped two cards" names an event and neither card; the two hands and slots are
     * the fact a player is trying to track. [cards] can be empty when the targets were not
     * carried, and the renderer falls back to the unnamed line.
     */
    data class SwappedTwo(
        override val who: Speaker,
        val cards: List<ChosenCard> = emptyList(),
    ) : Say

    /** A Jack or Queen whose owner looked and then chose to change nothing. */
    data class LeftThemAlone(override val who: Speaker) : Say

    data class DeclaredRank(override val who: Speaker, val rank: Rank) : Say

    /** An Ace, pointed: [victim] draws a card they did not want. */
    data class MadeDraw(override val who: Speaker, val victim: Speaker) : Say

    /**
     * A bot has taken this seat over, because its person has been gone too long.
     *
     * The person is named rather than the bot: that the seat now reads as an element is on the
     * felt already, and what a table needs told is whose seat it was.
     */
    data class SeatCovered(override val who: Speaker) : Say

    /** And they are back — the seat is theirs again, and stops being drawn as a bot. */
    data class SeatBack(override val who: Speaker) : Say

    /** The deal is over and play starts. Nobody's move, so nobody's name. */
    data object RoundBegins : Say {
        override val who: Speaker get() = Speaker.Nobody
    }

    // ------------------------------------------------------------------ table talk

    /*
     * The coalition's phrasebook, rendered as lines like any other.
     *
     * Talk joins `Say` rather than growing a vocabulary beside it because it is the same kind
     * of thing — a line in the strip with somebody's name on it — and because everything that
     * makes `Say` work is what talk needs: a [Speaker] that decides the verb, one string per
     * sentence, and nineteen locales already wired to render them.
     *
     * The wire type stays separate (`TableTalk` in shapes): that one is what crosses a socket
     * and what a bot produces, and it carries seat ids where these carry a [Speaker].
     */

    /** Where the speaker thinks their hand stands: low, high, or out of it. */
    data class Standing(override val who: Speaker, val where: TableTalk.Standing.Where) : Say

    /** Holding that rank, and ready to throw it in if one lands. */
    data class WillShed(override val who: Speaker, val rank: Rank) : Say

    /** "Play for that hand." Advice, not an instruction — the lowest hand counts regardless. */
    data class PlayFor(override val who: Speaker, val seat: Speaker) : Say

    /** A request for one of somebody's cards. */
    data class GiveMe(override val who: Speaker, val from: Speaker, val position: Int) : Say

    /** The other half of the same trade: a card offered away. */
    data class TakeThis(override val who: Speaker, val position: Int) : Say

    /** Yes, no, wait, or "that leaves us worse off". */
    data class Answered(override val who: Speaker, val says: TableTalk.Answer.Says) : Say

    /**
     * "I have a suggestion for you."
     *
     * The move itself is not spelled out here on purpose: a proposal is *acted on* rather than
     * read, and phase 2 draws it as the recipient's own table with the move already aimed.
     * A line in the strip only has to say that one arrived and from whom.
     */
    data class Suggests(override val who: Speaker, val to: Speaker) : Say

    /** "I am going to move." Legibility, so three seats do not surprise each other. */
    data class WillMove(override val who: Speaker) : Say
}

/**
 * A sentence off the wire, as a line for the strip.
 *
 * Pure, and in `shared/client` rather than in the UI, so a test can read what the table would
 * have said without composing anything.
 */
fun spoken(talk: TableTalk, viewerId: String, nicknames: Map<String, String>): Say {
    fun who(id: String): Speaker =
        if (id == viewerId) Speaker.You else nicknames[id]?.let(Speaker::Named) ?: Speaker.Nobody

    return when (talk) {
        is TableTalk.Standing -> Say.Standing(who(talk.by), talk.where)
        is TableTalk.WillShed -> Say.WillShed(who(talk.by), talk.rank)
        is TableTalk.PlayFor -> Say.PlayFor(who(talk.by), who(talk.seat))
        is TableTalk.GiveMe -> Say.GiveMe(who(talk.by), who(talk.from), talk.position)
        is TableTalk.TakeThis -> Say.TakeThis(who(talk.by), talk.position)
        is TableTalk.Answer -> Say.Answered(who(talk.by), talk.says)
        is TableTalk.Proposal -> Say.Suggests(who(talk.by), who(talk.to))
        is TableTalk.IWill -> Say.WillMove(who(talk.by))
    }
}

/**
 * Whose move it was.
 *
 * A closed type rather than a string, because the difference between "you" and somebody else
 * is grammatical and not cosmetic: it decides the verb. [Named] carries a nickname, which is
 * the one piece of player-typed text in this file — fine here, where it is displayed, and
 * never allowed anywhere near an analytics event (see `AnalyticsEvent`).
 */
sealed interface Speaker {
    data object You : Speaker
    data class Named(val nickname: String) : Speaker

    /** For the things that happen to the table rather than to a player. */
    data object Nobody : Speaker
}
