package game.vinto.client

import game.vinto.shapes.Rank

/**
 * One thing the lesson says, as a *message* rather than as two paragraphs of English.
 *
 * The last piece of WORDS.md §6h, and the same move `Say`, `Label`, `Ask`, `Detail` and `Explains`
 * already made: `shared/client` has no Compose and no resources, so a sentence assembled here
 * is an English one whatever the phone is set to. The lesson was the largest holdout —
 * twenty-eight beats and roughly a hundred and thirty literals — and it is the one a player
 * who does not read English needs *most*, because it is the part of the app whose whole job
 * is explaining.
 *
 * **The name.** `Beat` was the obvious one and is taken — `Choreography.kt` has had a `Beat`
 * since the animation layer was built, and it means something entirely different. `Teaches`
 * reads correctly beside the five types this joins (`Say`, `Label`, `Ask`, `Detail`,
 * `Explains`), all of which are named for what the module is doing rather than for the noun.
 *
 * Every beat carries an [id], and the ids are not decoration: `Taught.talked` tracks which
 * beats have been said, and eighteen of these ids were already the `talkId` strings that
 * tracking used. Keeping them means the vault entry of a lesson somebody is halfway through
 * still means what it meant.
 *
 * **Why [id] rather than making `Taught.talked` a `Set<Teaches>`.** That was the plan written
 * down in WORDS.md §6h, and it is wrong for exactly the two beats that section identified as
 * irregular. [TossIn] and [VintoCalled] carry arguments, so two instances of the same beat are
 * *unequal* — a toss-in window with one thrower and the same window with two would be two
 * different entries in the set, and the lesson would say itself twice. The id is the beat's
 * identity; the arguments are what varies within it.
 */
sealed interface Teaches {

    /** Stable across renames and translations. What `Taught.talked` remembers. */
    val id: String

    // --- What the game is, said before anything is dealt with -----------------------------

    data object Welcome : Teaches {
        override val id = "welcome"
    }

    /**
     * What the game is *really* about, said second: you never look at your own cards, so
     * the whole round is knowing what you hold — and a high card that bought you a look
     * was a fair trade, early on.
     *
     * Added on the product owner's read of the old opening, which told the player that
     * every card counts and stopped there: a newcomer then throws every 9 back in fright,
     * which is how you finish a round holding five cards you know nothing about.
     */
    data object Memory : Teaches {
        override val id = "memory"
    }

    // --- The deck, card by card, in the order they get harder ------------------------------

    data object CardsNumbers : Teaches {
        override val id = "cards_numbers"
    }

    data object CardsOwn : Teaches {
        override val id = "cards_own"
    }

    data object CardsTheirs : Teaches {
        override val id = "cards_theirs"
    }

    data object CardsJack : Teaches {
        override val id = "cards_jack"
    }

    data object CardsQueen : Teaches {
        override val id = "cards_queen"
    }

    data object CardsKing : Teaches {
        override val id = "cards_king"
    }

    data object CardsKingName : Teaches {
        override val id = "cards_king_name"
    }

    data object CardsKingWhose : Teaches {
        override val id = "cards_king_whose"
    }

    data object CardsOdd : Teaches {
        override val id = "cards_odd"
    }

    // --- Where everything is ---------------------------------------------------------------

    data object Tour : Teaches {
        override val id = "tour"
    }

    data object Seats : Teaches {
        override val id = "seats"
    }

    data object Help : Teaches {
        override val id = "help"
    }

    // --- The end of the round, which is a different game ------------------------------------

    /**
     * Who called it. Carried as a [Speaker] rather than a name, so the renderer decides how a
     * person is addressed — and so the fallback for a caller who cannot be found in the view
     * is a translated word rather than the literal English "Somebody".
     */
    data class VintoCalled(val caller: Speaker) : Teaches {
        override val id = ID

        companion object {
            const val ID = "vinto"
        }
    }

    data object Coalition : Teaches {
        override val id = "coalition"
    }

    /**
     * The same two beats from the other chair: the learner called, so the three bots are the
     * coalition and the learner is the one hand they play against. The round is planned for
     * this to be the way it ends — see `TeachingDeal`.
     */
    data object YouCalled : Teaches {
        override val id = "you_called"
    }

    data object CoalitionAgainstYou : Teaches {
        override val id = "coalition_vs_you"
    }

    /**
     * The coalition has chosen whose hand it plays. Said once the leader is named, over the
     * final round, so the learner watches the other two work for that hand rather than
     * three bots taking three unrelated turns.
     */
    data class CoalitionLeader(val who: Speaker) : Teaches {
        override val id = ID

        companion object {
            const val ID = "coalition_leader"
        }
    }

    /**
     * A coalition member playing an action card in the final round, held while it is in
     * play so the rule can be read before the card does its work. Once per rank: the
     * taught round shows an Ace, a King and a 9, which are the three the learner never
     * holds, and this is where they are explained (product owner: "fully show the
     * coalition's play, explaining the rules while they are playing").
     */
    data class FinalPlay(val who: Speaker, val rank: Rank) : Teaches {
        override val id = "final_${rank.serialName}"
    }

    data object YourTurnToCall : Teaches {
        override val id = "your_turn_to_call"
    }

    data object Scoring : Teaches {
        override val id = "scoring"
    }

    data object Session : Teaches {
        override val id = "session"
    }

    // --- The beats derived from the position, which are not talk beats ----------------------

    data object OnlyLook : Teaches {
        override val id = "only_look"
    }

    data object PeeksEnd : Teaches {
        override val id = "peeks_end"
    }

    data object NameOnlySeen : Teaches {
        override val id = "name_only_seen"
    }

    /**
     * The toss-in window.
     *
     * [alsoThrewIn] is whoever else has already thrown into this one, named — watching an
     * opponent do it is the difference between "a prompt I dismiss" and "a thing the whole
     * table does at once". Empty for a window nobody else has used yet, which is the common
     * case and reads as an ordinary sentence with no prefix at all.
     */
    data class TossIn(val alsoThrewIn: List<String>) : Teaches {
        override val id = ID

        companion object {
            const val ID = "toss_in"
        }
    }

    /**
     * Somebody else's turn.
     *
     * [who] and [playing] name a bot and the action card it has in play, when it has one —
     * the coach's one line then says what a 9, a King or an Ace is doing as it is done,
     * which is how the learner meets the three cards the round never puts in their hand.
     * Both null while a bot is merely deciding, which has no heading, as before.
     */
    data class Watching(val who: Speaker? = null, val playing: Rank? = null) : Teaches {
        override val id = ID

        companion object {
            const val ID = "watching"
        }
    }

    /** The Queen has looked at both cards, and theirs is the better one: trade. */
    data object SwapThem : Teaches {
        override val id = "swap_them"
    }

    /** Or yours is: leave them where they are. */
    data object LeaveThem : Teaches {
        override val id = "leave_them"
    }

    /**
     * Every card in the hand is one the learner has seen and the total is at or below zero.
     * The round is planned to reach this at the end of their second turn.
     */
    data object CallNow : Teaches {
        override val id = "call_now"
    }

    /** The card going down is one the learner never looked at: put it down without a word. */
    data object DoNotGuess : Teaches {
        override val id = "do_not_guess"
    }

    data object AimIt : Teaches {
        override val id = "aim_it"
    }

    /** A card turned up by a peek, being looked at: the moment it becomes something to remember. */
    data object RememberIt : Teaches {
        override val id = "remember_it"
    }

    data object GiveUpWorst : Teaches {
        override val id = "give_up_worst"
    }

    /**
     * Nothing the player knows about is worse than the card in hand, so the slot to take
     * is one they have never looked at — a trade that costs nothing they know of and buys a
     * card they now know.
     */
    data object SwapBlind : Teaches {
        override val id = "swap_blind"
    }

    /** And when even that is a bad trade: go back and throw it away. */
    data object NothingWorse : Teaches {
        override val id = "nothing_worse"
    }

    data object KeepOrThrow : Teaches {
        override val id = "keep_or_throw"
    }

    data object TwoWaysToStart : Teaches {
        override val id = "two_ways_to_start"
    }

    data object EveryTurnStarts : Teaches {
        override val id = "every_turn_starts"
    }

    /** The answer to somebody pressing a button other than the one being pointed at. */
    data object Strayed : Teaches {
        override val id = "strayed"
    }
}

/**
 * What a glow or a ring on the table means, said once, the first time it appears.
 *
 * Four of them, and they were `String` ids paired with an English line built by `glossOnce`.
 * The id was already doing this type's job — `Taught.glossed` is a set of them — so this only
 * makes it a type and moves the words.
 */
enum class Gloss(val id: String) {
    /** A card that breathes can be touched right now. */
    PULSE("pulse"),

    /** The chip under the piles names the rank the window is open for. */
    TOSS("toss"),

    /** Every move is written down in this box. */
    LOG("log"),

    /** The green number counts what is left in the deck. */
    BADGE("badge"),
}
