package game.vinto.client

import game.vinto.engine.GameEngine
import game.vinto.engine.initializeGame
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What the table says, checked as *meaning* rather than as English.
 *
 * This test is the argument for WORDS.md §6h's change, made concrete. Before it, the only way to check
 * the move log was to assert on a sentence — which meant a test that would fail the day
 * somebody translated the app, and which said what the log currently *reads* rather than what
 * it is supposed to *mean*. `assertEquals(Say.DrewKnown(You, SEVEN), …)` cannot be broken by a
 * translation and cannot pass with the wrong card in it.
 */
class NarrationTest {

    private fun dealt() = initializeGame(seed = 42)

    /**
     * Past the setup peeks and into play.
     *
     * The two peeks belong to the person holding the phone — the bots do not take theirs
     * through the engine — and one `FinishSetup` starts the round. The validator has a phase
     * gate (ROOM.md §6r), so a `DrawCard` before this point is *refused* and the state comes back
     * unchanged, which is exactly how the first draft of this test quietly asserted nothing:
     * it read a narration of a move that never happened.
     */
    private fun playing(): GameState {
        var state = dealt()
        val you = state.players.first { it.isHuman }.id
        for (position in 0..1) {
            state = GameEngine.reduce(state, GameAction.PeekSetupCard(PositionPayload(you, position))).state
        }
        state = GameEngine.reduce(state, GameAction.ConfirmPeek(PlayerIdPayload(you))).state
        state = GameEngine.reduce(state, GameAction.FinishSetup(PlayerIdPayload(you))).state
        check(state.phase == GamePhase.PLAYING) { "the round never started: ${state.phase}" }
        return state
    }

    @Test
    fun everybodyIsToldWhatWasDrawnBecauseTheTableShowsIt() {
        val before = playing()
        val action = GameAction.DrawCard(PlayerIdPayload(before.players[before.currentPlayerIndex].id))
        val after = GameEngine.reduce(before, action).state
        val actor = before.players[before.currentPlayerIndex].id
        val drawn = after.pendingAction?.card?.rank

        // To the person who drew it: the rank, as "you".
        val toDrawer = narrate(action, before, after, viewerId = actor)
        assertIs<Say.DrewKnown>(toDrawer, "the drawer was not told what they drew")
        assertEquals(Speaker.You, toDrawer.who)
        assertEquals(drawn, toDrawer.rank)

        // And to everybody else, by name: the rules reveal a drawn card publicly and the felt
        // draws it face-up for every seat, so a log that hid the rank was hiding what the
        // table had just shown — a Joker off the deck with no line saying so.
        val other = before.players.first { it.id != actor }.id
        val toOthers = narrate(action, before, after, viewerId = other)
        assertIs<Say.DrewKnown>(toOthers, "somebody watching was not told what came off the deck")
        assertEquals(Speaker.Named(before.players.first { it.id == actor }.nickname), toOthers.who)
        assertEquals(drawn, toOthers.rank)
    }

    @Test
    fun theSpeakerIsGrammaticalRatherThanCosmetic() {
        // The whole reason `Speaker` is a type: "you" and a name take different verbs, and in
        // Belarusian or Ukrainian they take different sentences. A renderer needs to be told
        // which, not left to compare a string against the word "You".
        var before = dealt()
        val actor = before.players.first { it.isHuman }.id
        for (position in 0..1) {
            before = GameEngine.reduce(before, GameAction.PeekSetupCard(PositionPayload(actor, position))).state
        }
        before = GameEngine.reduce(before, GameAction.ConfirmPeek(PlayerIdPayload(actor))).state
        val action = GameAction.FinishSetup(PlayerIdPayload(actor))
        val after = GameEngine.reduce(before, action).state

        assertEquals(Say.RoundBegins, narrate(action, before, after, viewerId = actor))
        assertEquals(Speaker.Nobody, Say.RoundBegins.who, "the deal is nobody's move")
    }

    @Test
    fun aPrivateMomentIsNotNarrated() {
        // Confirming a peek says only that somebody stopped looking at a card the reader was
        // never shown. Putting it in the log spends a line saying nothing.
        val before = dealt()
        val actor = before.players[before.currentPlayerIndex].id
        for (action in listOf(
            GameAction.ConfirmPeek(PlayerIdPayload(actor)),
            GameAction.SkipPeek(PlayerIdPayload(actor)),
        )) {
            assertNull(narrate(action, before, before, viewerId = actor), "$action was narrated")
        }
    }

    @Test
    fun everyCaseCarriesEnoughToBeSaidInAnyLanguage() {
        // A renderer gets the speaker and the card, never a half-built sentence. The point of
        // the type is that there is nothing left for `shared/client` to have got wrong about
        // grammar, because it does not attempt any.
        val said: List<Say> = listOf(
            Say.DrewKnown(Speaker.You, Rank.SEVEN),
            Say.Drew(Speaker.Named("Ember")),
            Say.Took(Speaker.You, Rank.QUEEN),
            Say.Took(Speaker.You, null),
            Say.Swapped(Speaker.You, slot = 3, dropped = Rank.KING),
            Say.Swapped(Speaker.Named("Sky"), slot = 1, dropped = null),
            Say.ThrewAway(Speaker.You, Rank.ACE),
            Say.Played(Speaker.Named("Dune"), Rank.JACK),
            Say.TossedIn(Speaker.Named("Fern"), Rank.SIX),
            Say.CalledVinto(Speaker.You),
            Say.SwappedTwo(Speaker.You),
            Say.LeftThemAlone(Speaker.You),
            Say.DeclaredRank(Speaker.You, Rank.KING),
            Say.MadeDraw(Speaker.You, Speaker.Named("Dune")),
            Say.RoundBegins,
        )

        for (one in said) {
            // Nothing in a message is an assembled sentence: every field is a speaker, a rank
            // or a number, so a translator is never handed half of anything.
            assertEquals(one, one, "a message must be a value, comparable by what it means")
        }
        assertEquals(said.size, said.toSet().size, "two messages collided")
    }

    /**
     * An Ace's aim is narrated — "Don made you draw a card" — because a card landing in a
     * hand with no line saying why is the most confusing thing this table does. Reported
     * from a phone: the log said Don had declared and tossed an Ace, and then nothing.
     */
    @Test
    fun anAcePointedAtSomebodyIsSaidFromBothSides() {
        var state = playing()
        val you = state.players.first { it.isHuman }.id
        val victim = state.players.first { !it.isHuman }
        state = GameEngine.reduce(state, GameAction.SetNextDrawCard(RankPayload(Rank.ACE))).state
        state = GameEngine.reduce(state, GameAction.DrawCard(PlayerIdPayload(you))).state
        state = GameEngine.reduce(state, GameAction.UseCardAction(PlayerIdPayload(you))).state
        check(state.pendingAction?.card?.rank == Rank.ACE) { "the Ace is not in play" }

        val aim = GameAction.SelectActionTarget(SelectActionTargetPayload.Ace(you, victim.id))
        val after = GameEngine.reduce(state, aim).state
        check(after.players.first { it.id == victim.id }.cards.size == victim.cards.size + 1) {
            "the Ace made nobody draw"
        }

        assertEquals(
            Say.MadeDraw(Speaker.You, Speaker.Named(victim.nickname)),
            narrate(aim, state, after, you),
        )
        assertEquals(
            Say.MadeDraw(Speaker.Named(state.players.first { it.id == you }.nickname), Speaker.You),
            narrate(aim, state, after, victim.id),
        )
    }
}
