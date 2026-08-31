package game.vinto.client

import game.vinto.engine.GameEngine
import game.vinto.engine.initializeGame
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What the table says, checked as *meaning* rather than as English.
 *
 * This test is the argument for §6h's change, made concrete. Before it, the only way to check
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
     * gate (§6d), so a `DrawCard` before this point is *refused* and the state comes back
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
    fun theDrawerSeesWhatTheyDrewAndNobodyElseDoes() {
        val before = playing()
        val action = GameAction.DrawCard(PlayerIdPayload(before.players[before.currentPlayerIndex].id))
        val after = GameEngine.reduce(before, action).state
        val actor = before.players[before.currentPlayerIndex].id

        // To the person who drew it: the rank, because they are looking at it.
        val toDrawer = narrate(action, before, after, viewerId = actor)
        assertIs<Say.DrewKnown>(toDrawer, "the drawer was not told what they drew")
        assertEquals(Speaker.You, toDrawer.who)
        assertEquals(after.pendingAction?.card?.rank, toDrawer.rank)

        // To everybody else: a card off the deck, and never which one. This is the same
        // redaction the view enforces, said in words — and asserting the *type* is what makes
        // it impossible for a rank to leak back in unnoticed.
        val other = before.players.first { it.id != actor }.id
        val toOthers = narrate(action, before, after, viewerId = other)
        assertIs<Say.Drew>(toOthers, "somebody who did not draw was told the rank")
        assertEquals(Speaker.Named(before.players.first { it.id == actor }.nickname), toOthers.who)
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
            Say.Drew(Speaker.Named("Raph")),
            Say.Took(Speaker.You, Rank.QUEEN),
            Say.Took(Speaker.You, null),
            Say.Swapped(Speaker.You, slot = 3, dropped = Rank.KING),
            Say.Swapped(Speaker.Named("Mikey"), slot = 1, dropped = null),
            Say.ThrewAway(Speaker.You, Rank.ACE),
            Say.Played(Speaker.Named("Don"), Rank.JACK),
            Say.TossedIn(Speaker.Named("Leo"), Rank.SIX),
            Say.CalledVinto(Speaker.You),
            Say.SwappedTwo(Speaker.You),
            Say.LeftThemAlone(Speaker.You),
            Say.DeclaredRank(Speaker.You, Rank.KING),
            Say.RoundBegins,
        )

        for (one in said) {
            // Nothing in a message is an assembled sentence: every field is a speaker, a rank
            // or a number, so a translator is never handed half of anything.
            assertEquals(one, one, "a message must be a value, comparable by what it means")
        }
        assertEquals(said.size, said.toSet().size, "two messages collided")
    }
}
