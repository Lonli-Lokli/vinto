package game.vinto.app

import androidx.compose.runtime.Composable
import game.vinto.app.art.Res
import game.vinto.app.art.card_help_ace
import game.vinto.app.art.card_help_eight
import game.vinto.app.art.card_help_five
import game.vinto.app.art.card_help_four
import game.vinto.app.art.card_help_jack
import game.vinto.app.art.card_help_joker
import game.vinto.app.art.card_help_king
import game.vinto.app.art.card_help_nine
import game.vinto.app.art.card_help_queen
import game.vinto.app.art.card_help_seven
import game.vinto.app.art.card_help_six
import game.vinto.app.art.card_help_ten
import game.vinto.app.art.card_help_three
import game.vinto.app.art.card_help_two
import game.vinto.app.art.card_long_ace
import game.vinto.app.art.card_long_eight
import game.vinto.app.art.card_long_jack
import game.vinto.app.art.card_long_king
import game.vinto.app.art.card_long_nine
import game.vinto.app.art.card_long_queen
import game.vinto.app.art.card_long_seven
import game.vinto.app.art.card_long_ten
import game.vinto.app.art.card_name_ace
import game.vinto.app.art.card_name_eight
import game.vinto.app.art.card_name_five
import game.vinto.app.art.card_name_four
import game.vinto.app.art.card_name_jack
import game.vinto.app.art.card_name_joker
import game.vinto.app.art.card_name_king
import game.vinto.app.art.card_name_nine
import game.vinto.app.art.card_name_queen
import game.vinto.app.art.card_name_seven
import game.vinto.app.art.card_name_six
import game.vinto.app.art.card_name_ten
import game.vinto.app.art.card_name_three
import game.vinto.app.art.card_name_two
import game.vinto.shapes.Rank
import org.jetbrains.compose.resources.stringResource

/**
 * The cards, in the phone's language.
 *
 * `CARD_CONFIGS` in `shared/shapes` still holds a name, a long description and a help text for
 * every rank, and every one of them is English. That was invisible for as long as the "?" sheet
 * was English too — but the sheet's TEMPLATE was translated slice by slice (WORDS.md §6h) while
 * the nouns dropped into it were not, so a Russian player read a Russian sentence with "Queen"
 * and a paragraph of English help inside it. Reported as exactly that.
 *
 * So the words move here and the engine keeps the rules. Nothing in `shared/shapes` changes:
 * `CardConfig` is still what says a King is worth 0 and a Queen peeks at two cards, and this is
 * only what those things are CALLED.
 *
 * **`shortDescription` has no accessor here, deliberately.** It becomes `Card.actionText`, which
 * is inside the canonical hash that all 50 fixtures pin against TypeScript — translating it
 * diverges every recording. `CardCopyIsDataTest` fails loudly if anybody tries, and the absence
 * of a `cardShort()` below is the other half of that guard: there is nothing to call.
 *
 * Exhaustive `when` with no `else`, so a new rank is a compile error here rather than a card
 * that renders as a blank in twenty languages.
 */
@Composable
fun cardName(rank: Rank): String = stringResource(
    when (rank) {
        Rank.TWO -> Res.string.card_name_two
        Rank.THREE -> Res.string.card_name_three
        Rank.FOUR -> Res.string.card_name_four
        Rank.FIVE -> Res.string.card_name_five
        Rank.SIX -> Res.string.card_name_six
        Rank.SEVEN -> Res.string.card_name_seven
        Rank.EIGHT -> Res.string.card_name_eight
        Rank.NINE -> Res.string.card_name_nine
        Rank.TEN -> Res.string.card_name_ten
        Rank.JACK -> Res.string.card_name_jack
        Rank.QUEEN -> Res.string.card_name_queen
        Rank.KING -> Res.string.card_name_king
        Rank.ACE -> Res.string.card_name_ace
        Rank.JOKER -> Res.string.card_name_joker
    },
)

/**
 * What the card does, in a sentence — empty for the number cards, which do nothing.
 *
 * The empty string is the same answer `CardConfig.longDescription` gave, and callers already
 * branch on it (`teach_note_plain` versus the version with an action in it). Returning it from
 * here rather than from a resource keeps that branch a Kotlin decision instead of a string
 * every translator has to be told to leave blank.
 */
@Composable
fun cardLong(rank: Rank): String = when (rank) {
    Rank.TWO -> ""
    Rank.THREE -> ""
    Rank.FOUR -> ""
    Rank.FIVE -> ""
    Rank.SIX -> ""
    Rank.JOKER -> ""
    Rank.SEVEN -> stringResource(Res.string.card_long_seven)
    Rank.EIGHT -> stringResource(Res.string.card_long_eight)
    Rank.NINE -> stringResource(Res.string.card_long_nine)
    Rank.TEN -> stringResource(Res.string.card_long_ten)
    Rank.JACK -> stringResource(Res.string.card_long_jack)
    Rank.QUEEN -> stringResource(Res.string.card_long_queen)
    Rank.KING -> stringResource(Res.string.card_long_king)
    Rank.ACE -> stringResource(Res.string.card_long_ace)
}

/** The paragraph the "?" sheet prints under the card. */
@Composable
fun cardHelp(rank: Rank): String = stringResource(
    when (rank) {
        Rank.TWO -> Res.string.card_help_two
        Rank.THREE -> Res.string.card_help_three
        Rank.FOUR -> Res.string.card_help_four
        Rank.FIVE -> Res.string.card_help_five
        Rank.SIX -> Res.string.card_help_six
        Rank.SEVEN -> Res.string.card_help_seven
        Rank.EIGHT -> Res.string.card_help_eight
        Rank.NINE -> Res.string.card_help_nine
        Rank.TEN -> Res.string.card_help_ten
        Rank.JACK -> Res.string.card_help_jack
        Rank.QUEEN -> Res.string.card_help_queen
        Rank.KING -> Res.string.card_help_king
        Rank.ACE -> Res.string.card_help_ace
        Rank.JOKER -> Res.string.card_help_joker
    },
)
