package game.vinto.app

import game.vinto.client.LocalGame
import game.vinto.client.Vault
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload

/**
 * The states a store capture can ask the app to be in.
 *
 * `zdymak` photographs five scenes, and three of them are not screens you can navigate to — a
 * table mid-round, a finished round's scores, a lobby. Its config says so and left the scene ids
 * as names for shots somebody would take by hand. This is the handle that makes them capturable:
 * it names a STATE the app builds deterministically from a pinned seed, rather than a screen it
 * navigates to, which is the shape `zdymak.config.mjs` decided on (2026-09-02) after Palon's
 * `MarketingState`.
 *
 * ## It is debug-only, and that is a build-variant gate rather than an `if`
 *
 * On Android the handle reads an intent extra, and **an intent extra is an entry point any app on
 * the phone can use**. A runtime `if (BuildConfig.DEBUG)` would leave the code in the shipped
 * binary for somebody to find; a `src/debug` source set with a no-op twin in `src/release` means
 * the release build does not contain it at all. On iOS a launch argument can only be set by
 * whoever starts the process, so no variant is needed there.
 *
 * That decision is the owner's, recorded in `zdymak.config.mjs`, and it is why this file holds
 * only the *staging* — the platform half of the handle lives beside each entry point.
 *
 * ## Determinism is the whole point
 *
 * A screenshot set is regenerated on every release and must not shuffle between them, or every
 * store listing needs re-reviewing for a change nobody made. [MARKETING_SEED] is pinned, the bots
 * run on the calling thread rather than a dispatcher, and the moves below are fixed — so the
 * table in the shot is the same table next year.
 */
enum class MarketingScene(val id: String) {
    /** The first screen, with a game to continue so the fuller menu is the one photographed. */
    HOME("home"),

    /** The lesson. A screen, so nothing is staged. */
    TEACH("teach"),

    /** A round in progress: the peeks spent, a card drawn, something on the discard. */
    TABLE("table"),

    /** The same round, played out, showing what it came to. */
    SCORE("score"),

    /**
     * The online lobby.
     *
     * **Not staged, deliberately.** A lobby is a room and a room is the network: reaching one
     * means either opening a socket during a screenshot run — which makes the shot depend on a
     * live service and a room that still exists — or building a fake `RemoteRoom` that never
     * connects, which is a second implementation of the room's own state machine kept in step by
     * hand for one picture. `zdymak.config.mjs` reached the same two options and neither is worth
     * it. Asking for this scene puts the app on the online menu, which is a real screen, honestly
     * reached, and shows what online play offers.
     */
    LOBBY("lobby"),
    ;

    companion object {
        /** The scene named by a handle, or null for anything unrecognised. */
        fun named(id: String?): MarketingScene? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/**
 * One fixed seed, so every capture of the table is the same table.
 *
 * Not [freshSeed]: a screenshot that changes on every run is a listing that has to be looked at
 * again on every release to see whether anything actually changed.
 */
private const val MARKETING_SEED = 20_260_903L

/**
 * The staged game behind [MarketingScene.TABLE] and [MarketingScene.SCORE].
 *
 * Both scenes are the same round at two different moments, which is why one function makes it:
 * a capture run asks for both, and photographing two unrelated games would show two different
 * hands for what a player experiences as one continuous thing.
 *
 * `botDispatcher = null` keeps the bots on the calling thread. That is what makes this
 * reproducible — a dispatcher would let the opponents' moves interleave differently between runs,
 * and the table on the felt is mostly *their* cards.
 */
internal suspend fun stagedGame(vault: Vault, toTheEnd: Boolean): LocalGame {
    val game = LocalGame.start(vault, MARKETING_SEED, Difficulty.EASY, botDispatcher = null)
    val me = game.playerId

    // The two peeks every player is dealt, spent — a table still in `SETUP` shows face-down
    // cards and none of the game, which is the least informative frame there is.
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
    game.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

    // A drawn card, so the rail has something in it and the discard is not empty. Without this
    // the shot is a table nobody has touched.
    game.session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
    if (!toTheEnd) return game

    // `SCORE` is the round's OUTCOME, and reaching it takes more than calling Vinto.
    //
    // The call is made at the end of a turn, so the drawn card has to be dealt with first; then
    // the coalition each take one more turn, and only when those have run is the round over and
    // `LocalGame.result` non-null. Stopping at the call — which is what this did first — leaves
    // the FINAL ROUND banner on screen with the hands still face down. That is a perfectly good
    // picture and it is not the one `zdymak` asked for, and the difference is invisible unless
    // you know which of the two you were expecting.
    game.session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
    game.session.dispatch(GameAction.CallVinto(PlayerIdPayload(me)))

    // The coalition's turns are bots on the calling thread, so they have already run by here in
    // the ordinary case. The loop is for the one that has not: a round that needs another nudge
    // rather than one that will never finish, which is why it is bounded and why overrunning it
    // fails loudly instead of returning a half-played table to a screenshot.
    var nudges = 0
    while (game.result == null && nudges < MAX_NUDGES) {
        game.session.dispatch(GameAction.ProcessAiTurn(PlayerIdPayload(me)))
        nudges++
    }
    check(game.result != null) { "the staged round did not finish in $MAX_NUDGES turns" }
    return game
}

/** Four seats and a final round: a dozen is generous, and an unbounded loop is a hang. */
private const val MAX_NUDGES = 12
