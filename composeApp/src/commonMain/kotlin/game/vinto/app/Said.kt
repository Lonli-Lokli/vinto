package game.vinto.app

import androidx.compose.runtime.Composable
import game.vinto.app.art.Res
import game.vinto.app.art.ask_card_waiting
import game.vinto.app.art.ask_card_waiting_unknown
import game.vinto.app.art.ask_choose_any_card
import game.vinto.app.art.ask_choose_two
import game.vinto.app.art.ask_look_at_one_of_theirs
import game.vinto.app.art.ask_look_at_one_of_yours
import game.vinto.app.art.ask_look_at_two
import game.vinto.app.art.ask_look_at_two_of_yours
import game.vinto.app.art.ask_name_what_you_put_down
import game.vinto.app.art.ask_one_more_to_look_at
import game.vinto.app.art.ask_or
import game.vinto.app.art.ask_ready
import game.vinto.app.art.ask_remember_it
import game.vinto.app.art.ask_round_over
import game.vinto.app.art.ask_round_over_lowest
import game.vinto.app.art.ask_round_over_not_lowest
import game.vinto.app.art.ask_say_and_play
import game.vinto.app.art.ask_somebody_playing
import game.vinto.app.art.ask_swap_them
import game.vinto.app.art.ask_toss_in
import game.vinto.app.art.ask_toss_in_barred
import game.vinto.app.art.ask_waiting_for_others
import game.vinto.app.art.ask_watching
import game.vinto.app.art.ask_what_do_you_say
import game.vinto.app.art.ask_which_card_replaced
import game.vinto.app.art.ask_who_draws
import game.vinto.app.art.ask_who_plays_for_you
import game.vinto.app.art.ask_you_drew
import game.vinto.app.art.ask_you_drew_unknown
import game.vinto.app.art.ask_your_turn
import game.vinto.app.art.choice_back
import game.vinto.app.art.choice_call_vinto
import game.vinto.app.art.choice_continue
import game.vinto.app.art.choice_discard
import game.vinto.app.art.choice_done
import game.vinto.app.art.choice_draw_card
import game.vinto.app.art.choice_just_swap
import game.vinto.app.art.choice_leave_them
import game.vinto.app.art.choice_put_it_down
import game.vinto.app.art.choice_start_round
import game.vinto.app.art.choice_swap_cards
import game.vinto.app.art.choice_use_action
import game.vinto.app.art.choice_use_from_pile
import game.vinto.app.art.log_called_vinto
import game.vinto.app.art.log_declared
import game.vinto.app.art.log_draw_they
import game.vinto.app.art.log_draw_you
import game.vinto.app.art.log_drew_known
import game.vinto.app.art.log_left_alone
import game.vinto.app.art.log_play_they
import game.vinto.app.art.log_play_unknown_they
import game.vinto.app.art.log_play_unknown_you
import game.vinto.app.art.log_play_you
import game.vinto.app.art.log_round_begins
import game.vinto.app.art.log_swap_dropping_they
import game.vinto.app.art.log_swap_dropping_you
import game.vinto.app.art.log_swap_they
import game.vinto.app.art.log_swap_you
import game.vinto.app.art.log_swapped_two
import game.vinto.app.art.log_throw_they
import game.vinto.app.art.log_throw_unknown_they
import game.vinto.app.art.log_throw_unknown_you
import game.vinto.app.art.log_throw_you
import game.vinto.app.art.log_took
import game.vinto.app.art.log_took_unknown
import game.vinto.app.art.log_tossed_in
import game.vinto.app.art.log_tossed_in_unknown
import game.vinto.app.art.log_you
import game.vinto.client.Ask
import game.vinto.client.Label
import game.vinto.client.Say
import game.vinto.client.Speaker
import game.vinto.shapes.getCardName
import org.jetbrains.compose.resources.stringResource

/**
 * A [Say] in the phone's own language.
 *
 * This is the half of §6h's design that lives where the resources are. `shared/client` says
 * *what happened*; this says it in words. Splitting them is what lets the table's move log be
 * translated at all — the module that knows the game has no Compose and therefore no way to
 * reach a string table, and building a sentence there produced English whatever the phone was
 * set to.
 *
 * The `_you` / `_they` pairs below are the point. English conjugates with a suffix, so it is
 * tempting to build "draw" + "s" and pass the fragment to a translator; Belarusian and
 * Ukrainian want two different sentences, and a fragment is a half nobody can fix. Each form
 * is a whole line here, and picking between them is this function's only real job.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun said(say: Say): String {
    val name = speakerName(say.who)
    val you = say.who is Speaker.You

    return when (say) {
        is Say.DrewKnown -> stringResource(Res.string.log_drew_known, say.rank.serialName)

        is Say.Drew -> if (you) {
            stringResource(Res.string.log_draw_you)
        } else {
            stringResource(Res.string.log_draw_they, name)
        }

        is Say.Took -> if (say.rank != null) {
            stringResource(Res.string.log_took, name, say.rank!!.serialName)
        } else {
            stringResource(Res.string.log_took_unknown, name)
        }

        is Say.Swapped -> when {
            say.dropped == null && you -> stringResource(Res.string.log_swap_you, say.slot)
            say.dropped == null -> stringResource(Res.string.log_swap_they, name, say.slot)
            you -> stringResource(Res.string.log_swap_dropping_you, say.slot, say.dropped!!.serialName)
            else ->
                stringResource(Res.string.log_swap_dropping_they, name, say.slot, say.dropped!!.serialName)
        }

        is Say.ThrewAway -> when {
            say.rank == null && you -> stringResource(Res.string.log_throw_unknown_you)
            say.rank == null -> stringResource(Res.string.log_throw_unknown_they, name)
            you -> stringResource(Res.string.log_throw_you, say.rank!!.serialName)
            else -> stringResource(Res.string.log_throw_they, name, say.rank!!.serialName)
        }

        is Say.Played -> when {
            say.rank == null && you -> stringResource(Res.string.log_play_unknown_you)
            say.rank == null -> stringResource(Res.string.log_play_unknown_they, name)
            you -> stringResource(Res.string.log_play_you, say.rank!!.serialName)
            else -> stringResource(Res.string.log_play_they, name, say.rank!!.serialName)
        }

        is Say.TossedIn -> if (say.rank != null) {
            stringResource(Res.string.log_tossed_in, name, say.rank!!.serialName)
        } else {
            stringResource(Res.string.log_tossed_in_unknown, name)
        }

        is Say.CalledVinto -> stringResource(Res.string.log_called_vinto, name)
        is Say.SwappedTwo -> stringResource(Res.string.log_swapped_two, name)
        is Say.LeftThemAlone -> stringResource(Res.string.log_left_alone, name)
        is Say.DeclaredRank -> stringResource(Res.string.log_declared, name, say.rank.serialName)
        Say.RoundBegins -> stringResource(Res.string.log_round_begins)
    }
}

/**
 * What to call the person who acted.
 *
 * "You" is a translated word; a nickname is not — it is what somebody typed, and translating
 * it would be both impossible and rude.
 */
@Composable
internal fun speakerName(who: Speaker): String = when (who) {
    Speaker.You -> stringResource(Res.string.log_you)
    is Speaker.Named -> who.nickname
    Speaker.Nobody -> ""
}

/**
 * What a button says, in the phone's language.
 *
 * The counterpart to [said] for [Label]. The card's *name* on "Use Queen" comes from
 * `CARD_CONFIGS` — the same words the help sheet uses for it — rather than from the rank's
 * symbol, because this one is read as a sentence rather than as a mark on a card.
 */
@Composable
fun labelled(label: Label): String = when (label) {
    Label.Back -> stringResource(Res.string.choice_back)
    Label.StartRound -> stringResource(Res.string.choice_start_round)
    Label.DrawCard -> stringResource(Res.string.choice_draw_card)
    is Label.UseFromPile -> stringResource(Res.string.choice_use_from_pile, getCardName(label.rank))
    Label.UseAction -> stringResource(Res.string.choice_use_action)
    Label.SwapCards -> stringResource(Res.string.choice_swap_cards)
    Label.Discard -> stringResource(Res.string.choice_discard)
    Label.JustSwap -> stringResource(Res.string.choice_just_swap)
    Label.PutItDown -> stringResource(Res.string.choice_put_it_down)
    Label.LeaveThem -> stringResource(Res.string.choice_leave_them)
    Label.Continue -> stringResource(Res.string.choice_continue)
    Label.CallVinto -> stringResource(Res.string.choice_call_vinto)
    Label.Done -> stringResource(Res.string.choice_done)
}

/**
 * A stable key for a button, for the lesson's pointer and for tests.
 *
 * Deliberately **not** the rendered words: the whole point of [Label] is that identity and
 * display are different things, and a key built from a translated string would move when the
 * language did.
 */
fun keyOf(label: Label): String = when (label) {
    is Label.UseFromPile -> "use-from-pile"
    else -> label.toString()
}

/**
 * What the table is asking, in the phone's language.
 *
 * The third of these renderers, and the one that joins a list. `ask_or` is a *word*, so the
 * separator between two toss-in ranks belongs here rather than in the model — where it was,
 * hard-coded, in a module with no way to translate it.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun asked(ask: Ask): String = when (ask) {
    Ask.LookAtTwoOfYours -> stringResource(Res.string.ask_look_at_two_of_yours)
    Ask.OneMoreToLookAt -> stringResource(Res.string.ask_one_more_to_look_at)
    Ask.ReadyWhenYouAre -> stringResource(Res.string.ask_ready)
    Ask.YourTurn -> stringResource(Res.string.ask_your_turn)

    is Ask.YouDrew -> if (ask.rank != null) {
        stringResource(Res.string.ask_you_drew, ask.rank!!.serialName)
    } else {
        stringResource(Res.string.ask_you_drew_unknown)
    }

    Ask.WhichCardDoesItReplace -> stringResource(Res.string.ask_which_card_replaced)
    Ask.NameWhatYouArePuttingDown -> stringResource(Res.string.ask_name_what_you_put_down)
    Ask.WhatDoYouSayThisCardIs -> stringResource(Res.string.ask_what_do_you_say)
    Ask.SayWhatItIsAndPlayIt -> stringResource(Res.string.ask_say_and_play)
    Ask.LookAtOneOfYourOwn -> stringResource(Res.string.ask_look_at_one_of_yours)
    Ask.LookAtOneOfAnotherPlayers -> stringResource(Res.string.ask_look_at_one_of_theirs)
    Ask.ChooseAnyCard -> stringResource(Res.string.ask_choose_any_card)
    Ask.ChooseTwoFromDifferentPlayers -> stringResource(Res.string.ask_choose_two)
    Ask.LookAtTwoFromDifferentPlayers -> stringResource(Res.string.ask_look_at_two)
    Ask.SwapThem -> stringResource(Res.string.ask_swap_them)
    Ask.RememberIt -> stringResource(Res.string.ask_remember_it)
    Ask.WhoDrawsACard -> stringResource(Res.string.ask_who_draws)

    is Ask.TheCardIsWaiting -> if (ask.rank != null) {
        stringResource(Res.string.ask_card_waiting, ask.rank!!.serialName)
    } else {
        stringResource(Res.string.ask_card_waiting_unknown)
    }

    is Ask.TossIn -> {
        val ranks = ask.ranks.joinToString(stringResource(Res.string.ask_or)) { it.serialName }
        if (ask.barred) {
            stringResource(Res.string.ask_toss_in_barred, ranks)
        } else {
            stringResource(Res.string.ask_toss_in, ranks)
        }
    }

    Ask.WaitingForTheOthers -> stringResource(Res.string.ask_waiting_for_others)
    Ask.Watching -> stringResource(Res.string.ask_watching)
    is Ask.SomebodyIsPlaying -> stringResource(Res.string.ask_somebody_playing, speakerName(ask.who))
    is Ask.WhoPlaysForYou -> stringResource(Res.string.ask_who_plays_for_you, speakerName(ask.caller))

    is Ask.RoundOver -> when {
        ask.yours == null || ask.best == null -> stringResource(Res.string.ask_round_over)
        ask.yours == ask.best -> stringResource(Res.string.ask_round_over_lowest, ask.yours!!)
        else -> stringResource(Res.string.ask_round_over_not_lowest, ask.yours!!, ask.best!!)
    }
}
