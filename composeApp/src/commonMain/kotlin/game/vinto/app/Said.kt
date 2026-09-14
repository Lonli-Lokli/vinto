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
import game.vinto.app.art.ask_say_what_you_know
import game.vinto.app.art.ask_somebody_playing
import game.vinto.app.art.ask_swap_them
import game.vinto.app.art.ask_toss_in
import game.vinto.app.art.ask_toss_in_barred
import game.vinto.app.art.ask_waiting_for_others
import game.vinto.app.art.ask_watching
import game.vinto.app.art.ask_what_do_you_say
import game.vinto.app.art.ask_what_do_you_say_these
import game.vinto.app.art.ask_what_should_they_do
import game.vinto.app.art.ask_what_will_you_do
import game.vinto.app.art.ask_where_should_their_card_come_from
import game.vinto.app.art.ask_where_will_your_card_come_from
import game.vinto.app.art.ask_which_card_king_points_at
import game.vinto.app.art.ask_which_card_replaced
import game.vinto.app.art.ask_which_card_should_they_put_down
import game.vinto.app.art.ask_which_card_they_throw_in
import game.vinto.app.art.ask_which_card_will_it_look_at
import game.vinto.app.art.ask_which_card_will_you_put_down
import game.vinto.app.art.ask_which_card_you_throw_in
import game.vinto.app.art.ask_which_rank_should_they_declare
import game.vinto.app.art.ask_which_rank_they_throw_in
import game.vinto.app.art.ask_which_rank_throw_in
import game.vinto.app.art.ask_which_rank_will_you_declare
import game.vinto.app.art.ask_which_two_will_it_swap
import game.vinto.app.art.ask_which_way_round
import game.vinto.app.art.ask_who_draws
import game.vinto.app.art.ask_who_should_draw
import game.vinto.app.art.ask_you_drew
import game.vinto.app.art.ask_you_drew_unknown
import game.vinto.app.art.ask_you_playing
import game.vinto.app.art.ask_your_turn
import game.vinto.app.art.beat_aim_it_body
import game.vinto.app.art.beat_aim_it_title
import game.vinto.app.art.beat_call_now_body
import game.vinto.app.art.beat_call_now_title
import game.vinto.app.art.beat_cards_jack_body
import game.vinto.app.art.beat_cards_jack_title
import game.vinto.app.art.beat_cards_king_body
import game.vinto.app.art.beat_cards_king_name_body
import game.vinto.app.art.beat_cards_king_name_title
import game.vinto.app.art.beat_cards_king_title
import game.vinto.app.art.beat_cards_king_whose_body
import game.vinto.app.art.beat_cards_king_whose_title
import game.vinto.app.art.beat_cards_numbers_body
import game.vinto.app.art.beat_cards_numbers_title
import game.vinto.app.art.beat_cards_odd_body
import game.vinto.app.art.beat_cards_odd_title
import game.vinto.app.art.beat_cards_own_body
import game.vinto.app.art.beat_cards_own_title
import game.vinto.app.art.beat_cards_queen_body
import game.vinto.app.art.beat_cards_queen_title
import game.vinto.app.art.beat_cards_theirs_body
import game.vinto.app.art.beat_cards_theirs_title
import game.vinto.app.art.beat_coalition_body
import game.vinto.app.art.beat_coalition_title
import game.vinto.app.art.beat_coalition_vs_you_body
import game.vinto.app.art.beat_coalition_vs_you_title
import game.vinto.app.art.beat_disagreement_body
import game.vinto.app.art.beat_disagreement_title
import game.vinto.app.art.beat_do_not_guess_body
import game.vinto.app.art.beat_do_not_guess_title
import game.vinto.app.art.beat_every_turn_starts_body
import game.vinto.app.art.beat_every_turn_starts_title
import game.vinto.app.art.beat_final_play_ace
import game.vinto.app.art.beat_final_play_king
import game.vinto.app.art.beat_final_play_look
import game.vinto.app.art.beat_final_play_other
import game.vinto.app.art.beat_final_play_title
import game.vinto.app.art.beat_give_up_worst_body
import game.vinto.app.art.beat_give_up_worst_title
import game.vinto.app.art.beat_help_body
import game.vinto.app.art.beat_help_title
import game.vinto.app.art.beat_keep_or_throw_body
import game.vinto.app.art.beat_keep_or_throw_title
import game.vinto.app.art.beat_leave_them_body
import game.vinto.app.art.beat_leave_them_title
import game.vinto.app.art.beat_memory_body
import game.vinto.app.art.beat_memory_title
import game.vinto.app.art.beat_name_only_seen_body
import game.vinto.app.art.beat_name_only_seen_title
import game.vinto.app.art.beat_nothing_worse_body
import game.vinto.app.art.beat_nothing_worse_title
import game.vinto.app.art.beat_only_look_body
import game.vinto.app.art.beat_only_look_title
import game.vinto.app.art.beat_peeks_end_body
import game.vinto.app.art.beat_remember_it_body
import game.vinto.app.art.beat_remember_it_title
import game.vinto.app.art.beat_scoring_body
import game.vinto.app.art.beat_scoring_title
import game.vinto.app.art.beat_seats_body
import game.vinto.app.art.beat_seats_title
import game.vinto.app.art.beat_session_body
import game.vinto.app.art.beat_session_title
import game.vinto.app.art.beat_strayed_body
import game.vinto.app.art.beat_swap_blind_body
import game.vinto.app.art.beat_swap_blind_title
import game.vinto.app.art.beat_swap_them_body
import game.vinto.app.art.beat_swap_them_title
import game.vinto.app.art.beat_toss_in_also
import game.vinto.app.art.beat_toss_in_body
import game.vinto.app.art.beat_toss_in_title
import game.vinto.app.art.beat_tour_body
import game.vinto.app.art.beat_tour_title
import game.vinto.app.art.beat_two_ways_to_start_body
import game.vinto.app.art.beat_two_ways_to_start_title
import game.vinto.app.art.beat_vinto_body
import game.vinto.app.art.beat_vinto_somebody
import game.vinto.app.art.beat_vinto_title
import game.vinto.app.art.beat_watching_body
import game.vinto.app.art.beat_watching_title
import game.vinto.app.art.beat_welcome_body
import game.vinto.app.art.beat_welcome_title
import game.vinto.app.art.beat_you_called_body
import game.vinto.app.art.beat_you_called_title
import game.vinto.app.art.beat_your_turn_to_call_body
import game.vinto.app.art.beat_your_turn_to_call_title
import game.vinto.app.art.board_carry_hint
import game.vinto.app.art.board_owner_named
import game.vinto.app.art.board_owner_you
import game.vinto.app.art.board_step_bin
import game.vinto.app.art.board_step_declare
import game.vinto.app.art.board_step_declare_card
import game.vinto.app.art.board_step_declare_unnamed
import game.vinto.app.art.board_step_force_draw
import game.vinto.app.art.board_step_peek
import game.vinto.app.art.board_step_peek_two
import game.vinto.app.art.board_step_point
import game.vinto.app.art.board_step_put_down
import game.vinto.app.art.board_step_put_down_called
import game.vinto.app.art.board_step_put_down_known
import game.vinto.app.art.board_step_swap
import game.vinto.app.art.board_step_take_discard
import game.vinto.app.art.board_step_then
import game.vinto.app.art.board_step_use_it
import game.vinto.app.art.board_title
import game.vinto.app.art.card_position
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
import game.vinto.app.art.detail_ace_hurts_your_side
import game.vinto.app.art.detail_barred
import game.vinto.app.art.detail_barred_card
import game.vinto.app.art.detail_card_does
import game.vinto.app.art.detail_changed_by
import game.vinto.app.art.detail_claim_was_wrong
import game.vinto.app.art.detail_deck_ran_out
import game.vinto.app.art.detail_king_declared
import game.vinto.app.art.detail_name_the_ranks
import game.vinto.app.art.detail_plan_is_a_suggestion
import game.vinto.app.art.detail_right_plays
import game.vinto.app.art.detail_scored_against
import game.vinto.app.art.detail_tap_to_say
import game.vinto.app.art.detail_the_caller_reads
import game.vinto.app.art.detail_touch_a_card_to_throw
import game.vinto.app.art.detail_touch_a_word
import game.vinto.app.art.detail_touch_the_card
import game.vinto.app.art.detail_wrong_costs
import game.vinto.app.art.explains_card_action
import game.vinto.app.art.explains_card_plain
import game.vinto.app.art.explains_final_round
import game.vinto.app.art.explains_scoring
import game.vinto.app.art.explains_setup
import game.vinto.app.art.explains_toss_in
import game.vinto.app.art.explains_turn
import game.vinto.app.art.gloss_log
import game.vinto.app.art.gloss_pulse
import game.vinto.app.art.gloss_toss
import game.vinto.app.art.label_agree
import game.vinto.app.art.label_another_rank
import game.vinto.app.art.label_decline_suggestion
import game.vinto.app.art.label_do_as_suggested
import game.vinto.app.art.label_let_the_card_go
import game.vinto.app.art.label_not_sure_which_way
import game.vinto.app.art.label_play_the_card
import game.vinto.app.art.label_put_a_card_down
import game.vinto.app.art.label_rank_said_by
import game.vinto.app.art.label_ready
import game.vinto.app.art.label_remove_throw
import game.vinto.app.art.label_say_it
import game.vinto.app.art.label_this_way_round
import game.vinto.app.art.label_well_see
import game.vinto.app.art.label_withdraw
import game.vinto.app.art.list_join_and
import game.vinto.app.art.log_called_vinto
import game.vinto.app.art.log_declared
import game.vinto.app.art.log_draw_they
import game.vinto.app.art.log_draw_you
import game.vinto.app.art.log_drew_known
import game.vinto.app.art.log_drew_known_they
import game.vinto.app.art.log_left_alone
import game.vinto.app.art.log_made_draw_they
import game.vinto.app.art.log_made_draw_you
import game.vinto.app.art.log_made_you_draw
import game.vinto.app.art.log_play_they
import game.vinto.app.art.log_play_unknown_they
import game.vinto.app.art.log_play_unknown_you
import game.vinto.app.art.log_play_you
import game.vinto.app.art.log_round_begins
import game.vinto.app.art.log_seat_back
import game.vinto.app.art.log_seat_back_you
import game.vinto.app.art.log_seat_covered
import game.vinto.app.art.log_seat_covered_you
import game.vinto.app.art.log_swap_dropping_they
import game.vinto.app.art.log_swap_dropping_you
import game.vinto.app.art.log_swap_they
import game.vinto.app.art.log_swap_you
import game.vinto.app.art.log_swapped_two
import game.vinto.app.art.log_swapped_two_named
import game.vinto.app.art.log_throw_they
import game.vinto.app.art.log_throw_unknown_they
import game.vinto.app.art.log_throw_unknown_you
import game.vinto.app.art.log_throw_you
import game.vinto.app.art.log_took
import game.vinto.app.art.log_took_unknown
import game.vinto.app.art.log_toss_in_missed
import game.vinto.app.art.log_toss_in_missed_unknown
import game.vinto.app.art.log_tossed_in
import game.vinto.app.art.log_tossed_in_unknown
import game.vinto.app.art.log_you
import game.vinto.app.art.says_add_throw
import game.vinto.app.art.says_and_then
import game.vinto.app.art.says_call_it
import game.vinto.app.art.says_draws
import game.vinto.app.art.says_drew
import game.vinto.app.art.says_drew_word
import game.vinto.app.art.says_forces
import game.vinto.app.art.says_lets_it_go
import game.vinto.app.art.says_looks_at_both
import game.vinto.app.art.says_looks_at_one
import game.vinto.app.art.says_looks_at_word
import game.vinto.app.art.says_names
import game.vinto.app.art.says_plays_it
import game.vinto.app.art.says_points_at
import game.vinto.app.art.says_points_at_what
import game.vinto.app.art.says_puts_down_whose
import game.vinto.app.art.says_puts_down_whose_known
import game.vinto.app.art.says_puts_down_word
import game.vinto.app.art.says_swaps_two
import game.vinto.app.art.says_takes
import game.vinto.app.art.says_takes_the_pile
import game.vinto.app.art.says_takes_word
import game.vinto.app.art.says_throws
import game.vinto.app.art.says_throws_blind
import game.vinto.app.art.says_throws_word
import game.vinto.app.art.says_well_see
import game.vinto.app.art.says_what_with
import game.vinto.app.art.says_which_card
import game.vinto.app.art.says_which_to_look_at
import game.vinto.app.art.says_which_to_point_at
import game.vinto.app.art.says_which_to_throw
import game.vinto.app.art.says_which_two
import game.vinto.app.art.says_who_draws
import game.vinto.app.art.says_you_throw
import game.vinto.app.art.says_you_throw_blind
import game.vinto.app.art.talk_bin
import game.vinto.app.art.talk_bin_they
import game.vinto.app.art.talk_give_me
import game.vinto.app.art.talk_give_me_they
import game.vinto.app.art.talk_high
import game.vinto.app.art.talk_high_they
import game.vinto.app.art.talk_low
import game.vinto.app.art.talk_low_they
import game.vinto.app.art.talk_no
import game.vinto.app.art.talk_no_they
import game.vinto.app.art.talk_play_for
import game.vinto.app.art.talk_play_for_they
import game.vinto.app.art.talk_suggests
import game.vinto.app.art.talk_suggests_they
import game.vinto.app.art.talk_take_this
import game.vinto.app.art.talk_take_this_they
import game.vinto.app.art.talk_wait
import game.vinto.app.art.talk_wait_they
import game.vinto.app.art.talk_will_move
import game.vinto.app.art.talk_will_move_they
import game.vinto.app.art.talk_will_shed
import game.vinto.app.art.talk_will_shed_they
import game.vinto.app.art.talk_worse
import game.vinto.app.art.talk_worse_they
import game.vinto.app.art.talk_yes
import game.vinto.app.art.talk_yes_they
import game.vinto.app.art.teach_note
import game.vinto.app.art.teach_note_plain
import game.vinto.client.Ask
import game.vinto.client.Detail
import game.vinto.client.Explains
import game.vinto.client.Gloss
import game.vinto.client.Label
import game.vinto.client.Say
import game.vinto.client.Says
import game.vinto.client.Speaker
import game.vinto.client.StepLine
import game.vinto.client.Teaches
import game.vinto.shapes.Rank
import game.vinto.shapes.TableTalk
import game.vinto.shapes.getCardConfig
import game.vinto.shapes.getCardName
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A [Say] in the phone's own language.
 *
 * This is the half of WORDS.md §6h's design that lives where the resources are. `shared/client` says
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
        is Say.Standing, is Say.WillShed, is Say.PlayFor, is Say.GiveMe, is Say.TakeThis,
        is Say.Answered, is Say.Suggests, is Say.WillMove,
        -> talked(say, name, you)

        is Say.DrewKnown -> if (you) {
            stringResource(Res.string.log_drew_known, say.rank.serialName)
        } else {
            stringResource(Res.string.log_drew_known_they, name, say.rank.serialName)
        }

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

        is Say.TossInMissed -> if (say.rank != null) {
            stringResource(Res.string.log_toss_in_missed, name, say.rank!!.serialName)
        } else {
            stringResource(Res.string.log_toss_in_missed_unknown, name)
        }

        is Say.CalledVinto -> stringResource(Res.string.log_called_vinto, name)
        is Say.SwappedTwo -> if (say.cards.size == 2) {
            stringResource(
                Res.string.log_swapped_two_named,
                name,
                stringResource(Res.string.card_position, speakerName(say.cards[0].who), say.cards[0].slot),
                stringResource(Res.string.card_position, speakerName(say.cards[1].who), say.cards[1].slot),
            )
        } else {
            stringResource(Res.string.log_swapped_two, name)
        }
        is Say.LeftThemAlone -> stringResource(Res.string.log_left_alone, name)
        is Say.DeclaredRank -> stringResource(Res.string.log_declared, name, say.rank.serialName)
        is Say.MadeDraw -> when {
            you -> stringResource(Res.string.log_made_draw_you, speakerName(say.victim))
            say.victim == Speaker.You -> stringResource(Res.string.log_made_you_draw, name)
            else -> stringResource(Res.string.log_made_draw_they, name, speakerName(say.victim))
        }
        is Say.SeatCovered -> if (you) {
            stringResource(Res.string.log_seat_covered_you)
        } else {
            stringResource(Res.string.log_seat_covered, name)
        }

        is Say.SeatBack -> if (you) {
            stringResource(Res.string.log_seat_back_you)
        } else {
            stringResource(Res.string.log_seat_back, name)
        }

        Say.RoundBegins -> stringResource(Res.string.log_round_begins)
    }
}

/**
 * "You are playing" against "Raph is playing".
 *
 * Written out as two whole sentences rather than one with the name swapped in: the verb
 * conjugates, and "is" against "are" is an English accident that a language with more of them
 * cannot repair from a fragment. Before this the viewer's own seat took the named form and
 * the table read **"You is playing"**, because the engine calls seat zero "You".
 */
@Composable
private fun whoIsPlaying(who: Speaker): String =
    if (who == Speaker.You) {
        stringResource(Res.string.ask_you_playing)
    } else {
        stringResource(Res.string.ask_somebody_playing, speakerName(who))
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
 * The counterpart to [said] for [Label].
 *
 * Detekt reads the `when` as complex; what it measures is the size of the button vocabulary,
 * not any difficulty in the code. Every arm is one `stringResource`, and an exhaustive `when`
 * with no `else` is the point — a new button becomes a compile error here rather than a blank
 * caption somebody notices in a screenshot.
 * The card's *name* on "Use Queen" comes from
 * `CARD_CONFIGS` — the same words the help sheet uses for it — rather than from the rank's
 * symbol, because this one is read as a sentence rather than as a mark on a card.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun labelled(label: Label): String = when (label) {
    Label.Withdraw -> stringResource(Res.string.label_withdraw)
    Label.DoAsSuggested -> stringResource(Res.string.label_do_as_suggested)
    Label.Ready -> stringResource(Res.string.label_ready)
    Label.SayIt -> stringResource(Res.string.label_say_it)
    Label.DeclineSuggestion -> stringResource(Res.string.label_decline_suggestion)
    is Label.ThisWayRound -> stringResource(
        Res.string.label_this_way_round,
        label.firstRank.serialName,
        label.secondRank.serialName,
    )
    Label.NotSureWhichWayRound -> stringResource(Res.string.label_not_sure_which_way)
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
    Label.Agree -> stringResource(Res.string.label_agree)
    Label.PlayTheCard -> stringResource(Res.string.label_play_the_card)
    Label.PutACardDown -> stringResource(Res.string.label_put_a_card_down)
    Label.WellSee -> stringResource(Res.string.label_well_see)
    Label.LetTheCardGo -> stringResource(Res.string.label_let_the_card_go)
    is Label.RankSaidBy ->
        stringResource(Res.string.label_rank_said_by, label.rank.serialName, speakerName(label.who))
    Label.AnotherRank -> stringResource(Res.string.label_another_rank)
    Label.RemoveThrow -> stringResource(Res.string.label_remove_throw)
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
    is Label.RankSaidBy -> "rank-said-by"
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
    Ask.WhichWayRound -> stringResource(Res.string.ask_which_way_round)
    Ask.SayWhatYouKnow -> stringResource(Res.string.ask_say_what_you_know)
    Ask.ThePlan -> stringResource(Res.string.board_title)
    is Ask.WhichRankWillTheyThrowIn ->
        aboutTurn(ask.who, Res.string.ask_which_rank_throw_in, Res.string.ask_which_rank_they_throw_in)
    is Ask.WhichCardWillTheyThrowIn ->
        aboutTurn(ask.who, Res.string.ask_which_card_you_throw_in, Res.string.ask_which_card_they_throw_in)
    is Ask.WhichCardWillItLookAt ->
        stringResource(Res.string.ask_which_card_will_it_look_at, cardName(ask.rank))
    Ask.WhichCardDoesTheKingPointAt -> stringResource(Res.string.ask_which_card_king_points_at)
    Ask.WhoShouldDraw -> stringResource(Res.string.ask_who_should_draw)
    is Ask.WhatShouldTheyDo ->
        aboutTurn(ask.who, Res.string.ask_what_will_you_do, Res.string.ask_what_should_they_do)
    is Ask.WhereShouldTheirCardComeFrom -> aboutTurn(
        ask.who,
        Res.string.ask_where_will_your_card_come_from,
        Res.string.ask_where_should_their_card_come_from,
    )
    is Ask.WhichTwoWillItSwap -> stringResource(Res.string.ask_which_two_will_it_swap, cardName(ask.rank))
    is Ask.WhichRankShouldTheyDeclare -> aboutTurn(
        ask.who,
        Res.string.ask_which_rank_will_you_declare,
        Res.string.ask_which_rank_should_they_declare,
    )
    is Ask.WhichCardShouldTheyPutDown -> aboutTurn(
        ask.who,
        Res.string.ask_which_card_will_you_put_down,
        Res.string.ask_which_card_should_they_put_down,
    )
    Ask.WhatDoYouSayThisCardIs -> stringResource(Res.string.ask_what_do_you_say)
    Ask.WhatDoYouSayTheseCardsAre -> stringResource(Res.string.ask_what_do_you_say_these)
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
    is Ask.SomebodyIsPlaying -> whoIsPlaying(ask.who)

    is Ask.RoundOver -> when {
        ask.yours == null || ask.best == null -> stringResource(Res.string.ask_round_over)
        ask.yours == ask.best -> stringResource(Res.string.ask_round_over_lowest, ask.yours!!)
        else -> stringResource(Res.string.ask_round_over_not_lowest, ask.yours!!, ask.best!!)
    }
}

/**
 * The line under the prompt, in the phone's language.
 *
 * `longDescription` throughout, never `shortDescription`. That distinction is not stylistic:
 * `shortDescription` becomes `Card.actionText`, which is inside the canonical hash that all 50
 * fixtures pin against TypeScript, so it is data rather than copy and cannot be translated —
 * `CardCopyIsDataTest` in `shared/shapes` fails loudly if anybody forgets. The King's borrowed
 * line was built from exactly that field until this slice.
 *
 * Detekt reads the `when` as complex; what it measures is the size of the vocabulary, as with
 * [labelled] — one `stringResource` per arm, and no `else`.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun detailed(detail: Detail): String = when (detail) {
    // Named, priced and explained, because this line is now what the card's own picture used
    // to say by being there — the rail gives its column to the choice while an action is being
    // aimed, so "Peek at any two cards" would arrive with no card attached to it. Deliberately
    // the same shape as `rail_card_action`, which is the version drawn *beside* a card, so the
    // sentence does not change when the picture comes and goes.
    is Detail.WhatTheCardDoes -> stringResource(
        Res.string.detail_card_does,
        cardName(detail.rank),
        getCardConfig(detail.rank).value,
        cardLong(detail.rank),
    )

    is Detail.KingDeclared -> stringResource(
        Res.string.detail_king_declared,
        detail.rank.serialName,
        cardLong(detail.rank),
    )

    Detail.TapACardToSayWhatItIs -> stringResource(Res.string.detail_tap_to_say)
    Detail.AnAceOnlyHurtsYourOwnSide -> stringResource(Res.string.detail_ace_hurts_your_side)
    Detail.NameEveryRankItCouldBe -> stringResource(Res.string.detail_name_the_ranks)
    Detail.RightPlaysItWrongCostsACard -> stringResource(Res.string.detail_right_plays)
    Detail.AWrongOneCostsAPenaltyCard -> stringResource(Res.string.detail_wrong_costs)
    Detail.BarredFromThisCard -> stringResource(Res.string.detail_barred_card)
    Detail.BarredForTheRestOfTheRound -> stringResource(Res.string.detail_barred)
    is Detail.ScoredAgainstTheCaller ->
        stringResource(Res.string.detail_scored_against, speakerName(detail.caller))
    Detail.TheDeckRanOut -> stringResource(Res.string.detail_deck_ran_out)
    Detail.TouchTheCard -> stringResource(Res.string.detail_touch_the_card)
    Detail.APlanIsASuggestion -> stringResource(Res.string.detail_plan_is_a_suggestion)
    Detail.CarryACardOrTouchIt -> stringResource(Res.string.board_carry_hint)
    Detail.AClaimWasWrong -> stringResource(Res.string.detail_claim_was_wrong)
    Detail.TouchAWord -> stringResource(Res.string.detail_touch_a_word)
    is Detail.ChangedBy -> stringResource(Res.string.detail_changed_by, speakerName(detail.who))
    Detail.TouchACardToThrow -> stringResource(Res.string.detail_touch_a_card_to_throw)
    Detail.TheCallerReads -> stringResource(Res.string.detail_the_caller_reads)
}

/**
 * A question about somebody's turn: the "you" form for the viewer's own, the named form for
 * anybody else's. Two resources rather than one with a name in it, because "What will you do"
 * and "What should Nina do" are different sentences in most of the nineteen languages.
 */
@Composable
private fun aboutTurn(who: Speaker, yours: StringResource, theirs: StringResource): String =
    if (who == Speaker.You) stringResource(yours) else stringResource(theirs, speakerName(who))

/**
 * One step of the plan, as a sentence fragment: what a lane asks its owner to do.
 *
 * The possessive is its own string per speaker rather than an apostrophe appended in code,
 * because "your" and "Nina's" are different words in every language this renders into, and a
 * translator handed "%1$s's" cannot fix the half they were not given.
 */
@Composable
fun stepWords(step: StepLine): String = when (step) {
    is StepLine.Swap -> stringResource(
        Res.string.board_step_swap,
        whose(step.fromWho),
        step.fromSlot,
        whose(step.toWho),
        step.toSlot,
    )

    is StepLine.Declare -> declareWords(step)
    is StepLine.Peek -> peekWords(step)
    is StepLine.ForceDraw -> stringResource(Res.string.board_step_force_draw, speakerName(step.who))
    StepLine.TakeTheDiscard -> stringResource(Res.string.board_step_take_discard)
    StepLine.Bin -> stringResource(Res.string.board_step_bin)
    StepLine.UseIt -> stringResource(Res.string.board_step_use_it)
    is StepLine.PutDown -> putDownWords(step)
}

/**
 * A King's declare — pointed at a card where the plan names one, with the rank where it has
 * been said — and what that card then does. The King points first and names second, so a
 * step can stand with the card and no rank yet.
 */
@Composable
private fun declareWords(step: StepLine.Declare): String {
    // Bound locally: a smart cast on a property from another module is not allowed.
    val who = step.who
    val slot = step.slot
    val rank = step.rank
    val named = when {
        who != null && slot != null && rank != null ->
            stringResource(Res.string.board_step_declare_card, whose(who), slot, rank.serialName)
        who != null && slot != null -> stringResource(Res.string.board_step_point, whose(who), slot)
        rank != null -> stringResource(Res.string.board_step_declare, rank.serialName)
        else -> stringResource(Res.string.board_step_declare_unnamed)
    }
    val then = step.then
    return if (then == null) named else stringResource(Res.string.board_step_then, named, stepWords(then))
}

/** A look at one card, or a Queen's at two. */
@Composable
private fun peekWords(step: StepLine.Peek): String {
    val alsoWho = step.alsoWho
    val alsoSlot = step.alsoSlot
    return if (alsoWho != null && alsoSlot != null) {
        stringResource(
            Res.string.board_step_peek_two,
            whose(step.who),
            step.slot,
            whose(alsoWho),
            alsoSlot,
        )
    } else {
        stringResource(Res.string.board_step_peek, whose(step.who), step.slot)
    }
}

/**
 * A put-down, called or silent, and what the call then does — "…and call it Jack, then swap
 * your card 1 with Tide’s card 2": one sentence, because the trade is what the call is for.
 */
@Composable
private fun putDownWords(step: StepLine.PutDown): String {
    // Bound locally: a smart cast on a property from another module is not allowed.
    val rank = step.rank
    val call = step.call
    val then = step.then
    val putDown = when {
        // A called card is named by its call, which is the rank the table knew it to be.
        call != null -> stringResource(
            Res.string.board_step_put_down_called,
            whose(step.who),
            step.slot,
            cardName(call),
        )

        rank == null -> stringResource(Res.string.board_step_put_down, whose(step.who), step.slot)
        else -> stringResource(
            Res.string.board_step_put_down_known,
            whose(step.who),
            step.slot,
            rank.serialName,
        )
    }
    return if (then == null) putDown else stringResource(Res.string.board_step_then, putDown, stepWords(then))
}

/**
 * One word of a planned turn, said whole: what a screen reader is given for the chip, and what
 * the plan's row on the live rail reads. The sentence is [Says] in the model; these are its
 * words in the phone's language, one resource per word so a translator sees each one whole.
 *
 * The chip itself draws less — see [chipWords] — because the cards a word names are drawn
 * there as cards: "puts down" beside a picture of the card, rather than "puts down your card
 * 2, the Jack" beside the same picture.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun saysWords(says: Says): String = when (says) {
    Says.Draws -> stringResource(Res.string.says_draws)
    is Says.Takes -> takesWords(says)
    is Says.Drew -> stringResource(Res.string.says_drew, cardName(says.rank))
    Says.WellSee -> stringResource(Res.string.says_well_see)
    Says.AndThen -> stringResource(Res.string.says_and_then)
    Says.WhatWith -> stringResource(Res.string.says_what_with)
    Says.PlaysIt -> stringResource(Res.string.says_plays_it)
    is Says.PutsDown -> putsDownWords(says)
    Says.WhichCard -> stringResource(Res.string.says_which_card)
    Says.LetsItGo -> stringResource(Res.string.says_lets_it_go)
    is Says.CallIt -> stringResource(Res.string.says_call_it, cardName(says.rank))
    is Says.Trade -> tradeWords(says)
    Says.WhichTwo -> stringResource(Res.string.says_which_two)
    is Says.Looks -> stringResource(Res.string.says_looks_at_one, whose(says.card.who), says.card.slot)
    Says.WhichToLookAt -> stringResource(Res.string.says_which_to_look_at)
    is Says.Points -> pointsWords(says)
    Says.WhichToPointAt -> stringResource(Res.string.says_which_to_point_at)
    is Says.Names -> stringResource(Res.string.says_names, says.rank.serialName)
    is Says.Forces -> stringResource(Res.string.says_forces, speakerName(says.who))
    Says.WhoDraws -> stringResource(Res.string.says_who_draws)
    is Says.Throws -> throwsWords(says)
    Says.WhichToThrow -> stringResource(Res.string.says_which_to_throw)
    Says.AddThrow -> stringResource(Res.string.says_add_throw)
}

/**
 * The word as the chip draws it, beside the cards it names: the verb and nothing the picture
 * already says. Every word that names no card is the same as its spoken form.
 */
@Composable
fun chipWords(says: Says): String = when (says) {
    is Says.Takes -> if (says.rank == null) {
        stringResource(
            Res.string.says_takes_the_pile,
        )
    } else {
        stringResource(Res.string.says_takes_word)
    }
    is Says.Drew -> stringResource(Res.string.says_drew_word)
    is Says.PutsDown -> stringResource(Res.string.says_puts_down_word)
    is Says.Looks -> stringResource(Res.string.says_looks_at_word)
    is Says.Throws -> stringResource(Res.string.says_throws_word)
    is Says.Trade, is Says.Points -> ""
    Says.Draws, Says.WellSee, Says.AndThen, Says.WhatWith, Says.PlaysIt, Says.WhichCard, Says.LetsItGo,
    is Says.CallIt, Says.WhichTwo, Says.WhichToLookAt, Says.WhichToPointAt, is Says.Names, is Says.Forces,
    Says.WhoDraws, Says.WhichToThrow, Says.AddThrow,
    -> saysWords(says)
}

@Composable
private fun takesWords(says: Says.Takes): String {
    val rank = says.rank
    return if (rank == null) {
        stringResource(Res.string.says_takes_the_pile)
    } else {
        stringResource(Res.string.says_takes, cardName(rank))
    }
}

@Composable
private fun putsDownWords(says: Says.PutsDown): String {
    val card = says.card
    val rank = card.rank
    return if (rank == null) {
        stringResource(Res.string.says_puts_down_whose, whose(card.who), card.slot)
    } else {
        stringResource(Res.string.says_puts_down_whose_known, whose(card.who), card.slot, rank.serialName)
    }
}

/** A Jack's swap, or a Queen's look at two — which is what the arrow between them says. */
@Composable
private fun tradeWords(says: Says.Trade): String = stringResource(
    if (says.swap) Res.string.says_swaps_two else Res.string.says_looks_at_both,
    whose(says.from.who),
    says.from.slot,
    whose(says.to.who),
    says.to.slot,
)

/** The card a King points at, and what it is said to be — or the question, while nobody has said. */
@Composable
private fun pointsWords(says: Says.Points): String {
    val rank = says.rank
    return if (rank == null) {
        stringResource(Res.string.says_points_at_what, whose(says.card.who), says.card.slot)
    } else {
        stringResource(Res.string.says_points_at, whose(says.card.who), says.card.slot, rank.serialName)
    }
}

/**
 * "then you throw in a five", or "then Nina throws in a five": the you-form is its own sentence.
 * A throw the table cannot vouch for names the card and says it is blind.
 */
@Composable
private fun throwsWords(says: Says.Throws): String {
    val you = says.who == Speaker.You
    val rank = says.rank
    val slot = says.card?.slot
    return when {
        rank != null && !says.blind && you -> stringResource(Res.string.says_you_throw, rank.serialName)
        rank != null && !says.blind ->
            stringResource(Res.string.says_throws, speakerName(says.who), rank.serialName)
        you -> stringResource(Res.string.says_you_throw_blind, slot ?: 0)
        else -> stringResource(Res.string.says_throws_blind, speakerName(says.who), slot ?: 0)
    }
}

/** "your", or "Nina's": the owner of a card, as a sentence needs it. */
@Composable
internal fun whose(who: Speaker): String = when (who) {
    Speaker.You -> stringResource(Res.string.board_owner_you)
    is Speaker.Named -> stringResource(Res.string.board_owner_named, who.nickname)
    Speaker.Nobody -> ""
}

/**
 * What the "?" explains, in the phone's language.
 *
 * The card paragraph is assembled from four pieces of `CARD_CONFIGS` — name, value,
 * `longDescription`, `helpText` — and the *order* of those pieces is now the resource's
 * business rather than the model's, which is the point of the whole exercise: a language that
 * wants the value first can have it.
 *
 * `shortDescription` is conspicuously absent and must stay so: it is `Card.actionText`, inside
 * the canonical hash (`CardCopyIsDataTest`).
 */
@Composable
fun explained(explains: Explains): String = when (explains) {
    is Explains.TheCardInPlay -> {
        val config = getCardConfig(explains.rank)
        if (config.action == null) {
            stringResource(Res.string.explains_card_plain, cardName(explains.rank), config.value)
        } else {
            stringResource(
                Res.string.explains_card_action,
                cardName(explains.rank),
                config.value,
                cardLong(explains.rank),
                cardHelp(explains.rank),
            )
        }
    }

    Explains.HowSetupWorks -> stringResource(Res.string.explains_setup)
    Explains.HowScoringWorks -> stringResource(Res.string.explains_scoring)
    Explains.HowTossingInWorks -> stringResource(Res.string.explains_toss_in)
    Explains.HowTheFinalRoundWorks -> stringResource(Res.string.explains_final_round)
    Explains.HowATurnWorks -> stringResource(Res.string.explains_turn)
}

/**
 * A lesson beat's heading, in the phone's language — or null for a beat that has none.
 *
 * The null is the point rather than an oversight: two beats deliberately carry no heading, and
 * "does this beat have a heading" is a fact about the words, so it is answered here and not by
 * the model. See `Teaches` and WORDS.md §6h.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun taughtTitle(teaches: Teaches): String? = when (teaches) {
    Teaches.OnlyLook -> stringResource(Res.string.beat_only_look_title)
    Teaches.NameOnlySeen -> stringResource(Res.string.beat_name_only_seen_title)
    is Teaches.TossIn -> stringResource(Res.string.beat_toss_in_title)
    Teaches.AimIt -> stringResource(Res.string.beat_aim_it_title)
    Teaches.RememberIt -> stringResource(Res.string.beat_remember_it_title)
    Teaches.GiveUpWorst -> stringResource(Res.string.beat_give_up_worst_title)
    Teaches.SwapBlind -> stringResource(Res.string.beat_swap_blind_title)
    Teaches.NothingWorse -> stringResource(Res.string.beat_nothing_worse_title)
    Teaches.KeepOrThrow -> stringResource(Res.string.beat_keep_or_throw_title)
    Teaches.TwoWaysToStart -> stringResource(Res.string.beat_two_ways_to_start_title)
    Teaches.EveryTurnStarts -> stringResource(Res.string.beat_every_turn_starts_title)
    Teaches.Welcome -> stringResource(Res.string.beat_welcome_title)
    Teaches.Memory -> stringResource(Res.string.beat_memory_title)
    Teaches.CardsNumbers -> stringResource(Res.string.beat_cards_numbers_title)
    Teaches.CardsOwn -> stringResource(Res.string.beat_cards_own_title)
    Teaches.CardsTheirs -> stringResource(Res.string.beat_cards_theirs_title)
    Teaches.CardsJack -> stringResource(Res.string.beat_cards_jack_title)
    Teaches.CardsQueen -> stringResource(Res.string.beat_cards_queen_title)
    Teaches.CardsKing -> stringResource(Res.string.beat_cards_king_title)
    Teaches.CardsKingName -> stringResource(Res.string.beat_cards_king_name_title)
    Teaches.CardsKingWhose -> stringResource(Res.string.beat_cards_king_whose_title)
    Teaches.CardsOdd -> stringResource(Res.string.beat_cards_odd_title)
    Teaches.Tour -> stringResource(Res.string.beat_tour_title)
    Teaches.Seats -> stringResource(Res.string.beat_seats_title)
    Teaches.Help -> stringResource(Res.string.beat_help_title)
    Teaches.Coalition -> stringResource(Res.string.beat_coalition_title)
    Teaches.YouCalled -> stringResource(Res.string.beat_you_called_title)
    Teaches.CoalitionAgainstYou -> stringResource(Res.string.beat_coalition_vs_you_title)
    Teaches.Disagreement -> stringResource(Res.string.beat_disagreement_title)
    is Teaches.FinalPlay -> stringResource(
        Res.string.beat_final_play_title,
        speakerName(teaches.who),
        cardName(teaches.rank),
    )
    Teaches.SwapThem -> stringResource(Res.string.beat_swap_them_title)
    Teaches.LeaveThem -> stringResource(Res.string.beat_leave_them_title)
    Teaches.CallNow -> stringResource(Res.string.beat_call_now_title)
    Teaches.DoNotGuess -> stringResource(Res.string.beat_do_not_guess_title)
    Teaches.YourTurnToCall -> stringResource(Res.string.beat_your_turn_to_call_title)
    Teaches.Session -> stringResource(Res.string.beat_session_title)
    Teaches.Scoring -> stringResource(Res.string.beat_scoring_title)

    is Teaches.VintoCalled -> stringResource(
        Res.string.beat_vinto_title,
        // A caller the view could not name. Was the literal English "Somebody".
        (teaches.caller as? Speaker.Named)?.nickname
            ?: stringResource(Res.string.beat_vinto_somebody),
    )

    // A bot with an action card engaged is named with what the card does — "Mikey plays the
    // 9 — Peek at one card of another player" — which is how a learner meets the cards the
    // round never puts in their hand. A bot merely deciding has no heading.
    is Teaches.Watching -> teaches.playing?.let { rank ->
        stringResource(
            Res.string.beat_watching_title,
            speakerName(teaches.who ?: Speaker.Nobody),
            cardName(rank),
            cardLong(rank),
        )
    }

    // The one with no heading, and the one that is an aside rather than a beat.
    Teaches.PeeksEnd, Teaches.Strayed -> null
}

/** A lesson beat's words. */
@Composable
@Suppress("CyclomaticComplexMethod")
fun taughtBody(teaches: Teaches): String = when (teaches) {
    Teaches.OnlyLook -> stringResource(Res.string.beat_only_look_body)
    Teaches.PeeksEnd -> stringResource(Res.string.beat_peeks_end_body)
    Teaches.NameOnlySeen -> stringResource(Res.string.beat_name_only_seen_body)
    is Teaches.Watching -> stringResource(Res.string.beat_watching_body)
    Teaches.AimIt -> stringResource(Res.string.beat_aim_it_body)
    Teaches.RememberIt -> stringResource(Res.string.beat_remember_it_body)
    Teaches.GiveUpWorst -> stringResource(Res.string.beat_give_up_worst_body)
    Teaches.SwapBlind -> stringResource(Res.string.beat_swap_blind_body)
    Teaches.NothingWorse -> stringResource(Res.string.beat_nothing_worse_body)
    Teaches.KeepOrThrow -> stringResource(Res.string.beat_keep_or_throw_body)
    Teaches.TwoWaysToStart -> stringResource(Res.string.beat_two_ways_to_start_body)
    Teaches.EveryTurnStarts -> stringResource(Res.string.beat_every_turn_starts_body)
    Teaches.Welcome -> stringResource(Res.string.beat_welcome_body)
    Teaches.Memory -> stringResource(Res.string.beat_memory_body)
    Teaches.CardsNumbers -> stringResource(Res.string.beat_cards_numbers_body)
    Teaches.CardsOwn -> stringResource(Res.string.beat_cards_own_body)
    Teaches.CardsTheirs -> stringResource(Res.string.beat_cards_theirs_body)
    Teaches.CardsJack -> stringResource(Res.string.beat_cards_jack_body)
    Teaches.CardsQueen -> stringResource(Res.string.beat_cards_queen_body)
    Teaches.CardsKing -> stringResource(Res.string.beat_cards_king_body)
    Teaches.CardsKingName -> stringResource(Res.string.beat_cards_king_name_body)
    Teaches.CardsKingWhose -> stringResource(Res.string.beat_cards_king_whose_body)
    Teaches.CardsOdd -> stringResource(Res.string.beat_cards_odd_body)
    Teaches.Tour -> stringResource(Res.string.beat_tour_body)
    Teaches.Seats -> stringResource(Res.string.beat_seats_body)
    Teaches.Help -> stringResource(Res.string.beat_help_body)
    Teaches.Coalition -> stringResource(Res.string.beat_coalition_body)
    Teaches.YouCalled -> stringResource(Res.string.beat_you_called_body)
    Teaches.CoalitionAgainstYou -> stringResource(Res.string.beat_coalition_vs_you_body)
    Teaches.Disagreement -> stringResource(Res.string.beat_disagreement_body)
    is Teaches.FinalPlay -> finalPlayBody(teaches.rank)
    Teaches.SwapThem -> stringResource(Res.string.beat_swap_them_body)
    Teaches.LeaveThem -> stringResource(Res.string.beat_leave_them_body)
    Teaches.CallNow -> stringResource(Res.string.beat_call_now_body)
    Teaches.DoNotGuess -> stringResource(Res.string.beat_do_not_guess_body)
    Teaches.YourTurnToCall -> stringResource(Res.string.beat_your_turn_to_call_body)
    Teaches.Session -> stringResource(Res.string.beat_session_body)
    Teaches.Scoring -> stringResource(Res.string.beat_scoring_body)
    Teaches.Strayed -> stringResource(Res.string.beat_strayed_body)
    is Teaches.VintoCalled -> stringResource(Res.string.beat_vinto_body)

    is Teaches.TossIn -> alsoThrewIn(teaches.alsoThrewIn) +
        stringResource(Res.string.beat_toss_in_body)
}

/**
 * What a coalition member's card is about to do, by rank.
 *
 * Three the taught round has the bots play, each explained once; anything else a learner
 * who wandered off the line sees their bots play gets the general line.
 */
@Composable
private fun finalPlayBody(rank: Rank): String = when (rank) {
    Rank.ACE -> stringResource(Res.string.beat_final_play_ace)
    Rank.KING -> stringResource(Res.string.beat_final_play_king)
    Rank.NINE, Rank.TEN -> stringResource(Res.string.beat_final_play_look)
    Rank.SEVEN, Rank.EIGHT, Rank.JACK, Rank.QUEEN, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE,
    Rank.SIX, Rank.JOKER,
    -> stringResource(Res.string.beat_final_play_other)
}

/**
 * "Raph and Mikey just threw one in. ", or nothing at all.
 *
 * The joiner is a resource because " and " is a grammar decision, and the model hands over
 * names rather than a sentence for exactly that reason.
 */
@Composable
private fun alsoThrewIn(names: List<String>): String {
    if (names.isEmpty()) return ""
    val joined = names.joinToString(stringResource(Res.string.list_join_and))
    return stringResource(Res.string.beat_toss_in_also, joined)
}

/** What a glow or a ring on the table means. */
@Composable
fun glossed(gloss: Gloss): String = when (gloss) {
    Gloss.PULSE -> stringResource(Res.string.gloss_pulse)
    Gloss.TOSS -> stringResource(Res.string.gloss_toss)
    Gloss.LOG -> stringResource(Res.string.gloss_log)
}

/**
 * A card met for the first time, said in the game's own words.
 *
 * The words are `CardWords` — the same copy the help sheet shows, so the lesson cannot teach a
 * rule the rest of the game does not have. They used to be `CARD_CONFIGS` directly, which is
 * why a translated frame kept arriving around English nouns.
 */
@Composable
fun noteOn(rank: Rank): String {
    val value = getCardConfig(rank).value.toString()
    val long = cardLong(rank)
    return if (long.isEmpty()) {
        stringResource(Res.string.teach_note_plain, cardName(rank), value)
    } else {
        stringResource(Res.string.teach_note, cardName(rank), value, long)
    }
}

/**
 * The coalition's phrasebook, in the reader's own language.
 *
 * Split out of [said] because it is a whole vocabulary rather than a few more cases, and
 * because every one of these is somebody *speaking* — the strip is a record of what was said,
 * not of what is true, so "You say your hand is low" is the right sentence and a bare
 * assertion is not.
 *
 * Four renderers rather than one: the two enum-bearing sentences fan out further, and folding
 * every branch into a single `when` makes one nobody can read.
 */
@Composable
private fun talked(say: Say, name: String, you: Boolean): String = when (say) {
    is Say.Standing -> talkStanding(say, name, you)
    is Say.Answered -> talkAnswer(say, name, you)
    else -> talkPlainly(say, name, you)
}

/**
 * The sentences with nothing to fan out: a name, sometimes a rank or a card, and that is all.
 *
 * Detekt reads the pile of two-armed `if (you)` as complexity. What it is measuring is the
 * size of the vocabulary rather than any difficulty in the code — each arm is one
 * `stringResource` call, and the "you" form and the "they" form of a sentence are different
 * sentences in most of the nineteen languages this renders into, not a parameter.
 */
@Composable
@Suppress("CognitiveComplexMethod")
private fun talkPlainly(say: Say, name: String, you: Boolean): String = when (say) {
    is Say.WillShed -> if (you) {
        stringResource(Res.string.talk_will_shed, say.rank.serialName)
    } else {
        stringResource(Res.string.talk_will_shed_they, name, say.rank.serialName)
    }

    is Say.PlayFor -> if (you) {
        stringResource(Res.string.talk_play_for, speakerName(say.seat))
    } else {
        stringResource(Res.string.talk_play_for_they, name, speakerName(say.seat))
    }

    is Say.TakeThis -> if (you) {
        stringResource(Res.string.talk_take_this, say.position + 1)
    } else {
        stringResource(Res.string.talk_take_this_they, name, say.position + 1)
    }

    is Say.WillMove -> if (you) {
        stringResource(Res.string.talk_will_move)
    } else {
        stringResource(Res.string.talk_will_move_they, name)
    }

    is Say.Suggests -> if (you) {
        stringResource(Res.string.talk_suggests, speakerName(say.to))
    } else {
        stringResource(Res.string.talk_suggests_they, name)
    }

    is Say.GiveMe -> if (you) {
        stringResource(Res.string.talk_give_me, speakerName(say.from), say.position + 1)
    } else {
        stringResource(Res.string.talk_give_me_they, name, speakerName(say.from), say.position + 1)
    }

    else -> ""
}

/** Where the speaker says their hand stands. */
@Composable
private fun talkStanding(say: Say.Standing, name: String, you: Boolean): String = when (say.where) {
    TableTalk.Standing.Where.LOW ->
        if (you) stringResource(Res.string.talk_low) else stringResource(Res.string.talk_low_they, name)

    TableTalk.Standing.Where.HIGH ->
        if (you) stringResource(Res.string.talk_high) else stringResource(Res.string.talk_high_they, name)

    TableTalk.Standing.Where.BIN ->
        if (you) stringResource(Res.string.talk_bin) else stringResource(Res.string.talk_bin_they, name)
}

/** An answer to something somebody asked. */
@Composable
private fun talkAnswer(say: Say.Answered, name: String, you: Boolean): String = when (say.says) {
    TableTalk.Answer.Says.YES ->
        if (you) stringResource(Res.string.talk_yes) else stringResource(Res.string.talk_yes_they, name)

    TableTalk.Answer.Says.NO ->
        if (you) stringResource(Res.string.talk_no) else stringResource(Res.string.talk_no_they, name)

    TableTalk.Answer.Says.WAIT ->
        if (you) stringResource(Res.string.talk_wait) else stringResource(Res.string.talk_wait_they, name)

    TableTalk.Answer.Says.THAT_LEAVES_US_WORSE ->
        if (you) stringResource(Res.string.talk_worse) else stringResource(Res.string.talk_worse_they, name)
}
