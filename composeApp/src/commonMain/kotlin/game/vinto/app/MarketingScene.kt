package game.vinto.app

import game.vinto.client.LocalGame
import game.vinto.client.Vault
import game.vinto.engine.CardView
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.hasAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

    /**
     * The final round with the coalition's board open — the richest screen the game has, and
     * the only one that shows three players cooperating rather than one player's hand.
     *
     * Staged by playing the real game forward until a **bot** calls Vinto, because the caller
     * has no coalition and therefore no board: a round the human called would photograph the
     * one seat this screen is not about.
     */
    PLAN("plan"),

    /**
     * A round that plays ITSELF — the only scene meant to be filmed rather than photographed.
     *
     * The App Store preview slot wants footage of the game being played, and none of the scenes
     * above move: [TABLE] deals a position and waits for a human. This one keeps the seats taking
     * turns for as long as the camera is rolling. `demoGame` says how, and why the moves are real
     * ones through the ordinary validator rather than a puppet show.
     */
    DEMO("demo"),

    /**
     * A King naming a card, filmed. Your turn draws a King and names one of the two cards you
     * peeked at the deal — correctly, because you saw it — so the camera catches the whole
     * declaration: the name held up beside the King, the card lifted where it lay, the card flying
     * into the name, and the two going to the pile as one.
     */
    KING("king"),

    /** A Queen, filmed: your turn draws one, looks at a card on each side of the table, and swaps them. */
    QUEEN("queen"),

    /**
     * A Vinto call, filmed: [PLAN]'s deal played up to a few moves before a bot calls, and the
     * rest at a player's pace, so the camera sees the call land and the coalition being told.
     */
    CALL("call"),

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
 *
 * **This particular number was searched for rather than picked.** The first pinned seed staged a
 * round the player LOSES — 31 against the coalition's best 14 — which is honest and is a poor
 * thing to put on a store page. This one calls Vinto on a hand of 5 against their best 16, so the
 * score sheet a shopper sees reads "The Vinto call held". Nothing about the game is misrepresented
 * by choosing which real deal to photograph; every number on that sheet is one the engine produced.
 */
private const val MARKETING_SEED = 20_260_072L

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

/**
 * A game played forward until a bot calls Vinto, so the human is in the coalition and the
 * board exists.
 *
 * The human's turns are spent rather than played — draw, put it down — because the point of
 * the picture is what the *coalition* is doing and a staged hand that keeps improving would
 * take the round somewhere else. Everything here goes through the ordinary session: this is
 * the real engine reaching a real final round, not a table arranged to look like one.
 */
internal suspend fun coalitionGame(vault: Vault): LocalGame {
    // Off the main thread, unlike the other staged scenes. This one plays a dozen real turns to
    // reach a bot's call, which is a few hundred milliseconds on a developer's machine and tens
    // of seconds on a software-rendered emulator — long enough for Android to put up "Vinto!
    // isn't responding" over a capture. The dispatcher only moves where the search runs; every
    // dispatch is still awaited, so the round is the same one every time.
    val game = LocalGame.start(vault, COALITION_SEED, Difficulty.EASY, botDispatcher = Dispatchers.Default)
    val me = game.playerId
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
    game.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

    // One action per pass, and the loop stops the instant the call lands. A pass that sent a
    // whole turn's worth would run past the moment being staged: every dispatch is *acting*, and
    // acting is what closes the coalition's window.
    var acted = 0
    while (game.session.view.value.vintoCallerId == null && acted < MAX_ACTIONS) {
        stepTowardTheCall(game)
        acted++
    }

    val caller = game.session.view.value.vintoCallerId
    check(caller != null && caller != me) { "no bot called Vinto in $MAX_ACTIONS moves (caller=$caller)" }

    // For the picture, the call is not it: an empty board saying "no plan yet" is. What makes
    // that screen worth photographing is the coalition having spoken and the board seeded from
    // what they said, which is the first thing that happens once the window closes.
    game.session.doneConferring()

    var settling = 0
    while (game.session.plan.value?.lanes?.any { it.step != null } != true && settling < SETTLING_PASSES) {
        game.session.dispatch(GameAction.ProcessAiTurn(PlayerIdPayload(me)))
        settling++
    }
    return game
}

/**
 * One action of [coalitionGame]'s deal: the human's turn spent — draw, put it down — or the table's.
 * Shared with [CALL][MarketingScene.CALL], which has to reach the same call by the same road.
 */
private suspend fun stepTowardTheCall(game: LocalGame) {
    val me = game.playerId
    val view = game.session.view.value
    val mine = view.players.getOrNull(view.currentPlayerIndex)?.id == me
    when {
        view.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE ->
            game.session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))

        view.pendingAction?.playerId == me ->
            game.session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        mine -> game.session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

        else -> game.session.dispatch(GameAction.ProcessAiTurn(PlayerIdPayload(me)))
    }
}

/** The deal this scene is staged from. Fixed, so the picture is the same one every time. */
private const val COALITION_SEED = 20_260_079L

/**
 * A bound, not a budget: this deal reaches a bot's call in a fraction of it, and a round that
 * has not by here is a round where something else has gone wrong.
 */
private const val MAX_ACTIONS = 200

/** Long enough for three declarations and the seeding that follows them; not a whole round. */
private const val SETTLING_PASSES = 8

/**
 * The staged game behind [MarketingScene.DEMO], and the loop that keeps it moving.
 *
 * Every other scene is a photograph: a moment arranged, then handed to the screen to sit still
 * in. This one is footage. Apple's App Preview slot wants the game *being played*, and a clip of
 * [MarketingScene.TABLE] is a still with a soundtrack — the table deals a position and then waits
 * for a human who, on a capture machine, is never coming.
 *
 * **Not a rigged demo.** Every move below goes through the ordinary session and the ordinary
 * validator, and the opponents are the same MCTS bots a player meets. What is arranged is only
 * *that* the seats keep taking turns, not what any of them decides.
 *
 * The human's own turn is spent rather than played — draw it, put it down. A staged hand that
 * kept improving would steer the round, and the point of the footage is the table.
 */
internal suspend fun demoGame(vault: Vault): LocalGame {
    // Off the calling thread, unlike the photographed scenes. Those stage a fixed position and
    // stop, so keeping the bots on the caller is what makes them reproducible; this one plays for
    // as long as somebody is filming, and a search running on the frame-producing thread is a
    // recording of a stalled screen.
    val game = LocalGame.start(vault, DEMO_SEED, Difficulty.EASY, botDispatcher = Dispatchers.Default)
    val me = game.playerId
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
    game.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
    return game
}

/**
 * Keep [game] moving until the caller stops caring.
 *
 * Cancelled by the composition that started it, which is the whole reason it is a plain suspend
 * loop rather than something with a lifecycle: leaving the screen cancels the scope and the round
 * stops with it.
 *
 * The pause between moves is the point rather than an accident. The table has animations — a card
 * crossing the felt, a toss-in landing — and a loop that dispatched as fast as the engine can
 * reduce would finish the round behind its own choreography and film none of it. A reel that wants
 * the game to look faster speeds up the FOOTAGE; the app plays at the pace a player would see.
 */
internal suspend fun playOn(game: LocalGame) {
    val me = game.playerId
    while (currentCoroutineContext().isActive) {
        delay(DEMO_BEAT_MS)
        val view = game.session.view.value
        when {
            // A finished round is the one thing that stops by itself. Dealing the next one keeps
            // the camera fed, and it is the same button the score sheet offers a player.
            game.result != null -> {
                game.nextRound()
            }

            // The window after every discard. Nobody's turn advances until this seat says it is
            // done with it, and a loop that never says so stops on its second lap — which is how
            // the coalition scene first came out blank.
            view.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE -> {
                game.session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
            }

            view.players.getOrNull(view.currentPlayerIndex)?.id == me -> {
                game.session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
                delay(DEMO_BEAT_MS)
                game.session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            }

            else -> {
                game.session.dispatch(GameAction.ProcessAiTurn(PlayerIdPayload(me)))
            }
        }
    }
}

/**
 * A deal for the footage, and a different one from the photographed scenes on purpose.
 *
 * [MARKETING_SEED] was searched for a round the player WINS, because a score sheet is a still that
 * a shopper reads. This one is not read, it is watched, so what it wants is a round with things
 * happening in it rather than a flattering ending.
 */
private const val DEMO_SEED = 20_260_081L

/** One beat per move: long enough for the card to cross the felt, short enough to feel played. */
private const val DEMO_BEAT_MS = 900L

/**
 * The filmed action scenes ([MarketingScene.KING], [MarketingScene.QUEEN], [MarketingScene.CALL]).
 *
 * A game is staged here the way the photographed scenes are — a fixed deal, the bots on the calling
 * thread, so it is the same table every run — and the *moment* is played live afterwards, a beat at
 * a time, once the screen is up and the camera rolling. Played during staging it would already be
 * over when the first frame is drawn.
 *
 * **Chosen, not rigged.** The one arrangement is [GameAction.SetNextDrawCard], the session's own
 * way to put a card on top of the deck, so the turn draws the card being shown. Everything after it
 * is an ordinary move through the ordinary validator, and the King names a card the player really
 * saw — which is the only way a player can name one right.
 */
internal class Filmed(val game: LocalGame, val live: suspend (LocalGame) -> Unit)

/** Dealt, and the two peeks spent — reading what they showed while the deal still shows it. */
private suspend fun dealtAndPeeked(vault: Vault, seed: Long): Pair<LocalGame, List<Rank?>> {
    val game = LocalGame.start(vault, seed, Difficulty.EASY, botDispatcher = null)
    val me = game.playerId
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
    game.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
    // Your own cards are face up to you only during the deal; after it you remember them or not.
    val seen = game.session.view.value.players.first { it.id == me }.cards.take(2)
        .map { (it as? CardView.Visible)?.card?.rank }
    game.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
    return game to seen
}

private suspend fun drawAndUse(game: LocalGame, rank: Rank) {
    val me = PlayerIdPayload(game.playerId)
    delay(FILM_OPENING_MS)
    game.session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
    game.session.dispatch(GameAction.DrawCard(me))
    delay(FILM_BEAT_MS)
    game.session.dispatch(GameAction.UseCardAction(me))
    delay(FILM_BEAT_MS)
}

internal suspend fun kingFilm(vault: Vault): Filmed {
    val (game, seen) = dealtAndPeeked(vault, MARKETING_SEED)
    // A card with no action of its own, so a right name sends it to the pile and the turn ends
    // there. A named seven would go into play and wait for a decision nobody is there to make.
    val position = seen.indexOfFirst { it != null && !hasAction(it) }.takeIf { it >= 0 } ?: 0
    val rank = checkNotNull(seen[position]) { "the deal showed no card at $position" }
    return Filmed(game) { g ->
        val me = g.playerId
        drawAndUse(g, Rank.KING)
        val own = SelectActionTargetPayload.Positional(me, me, position)
        g.session.dispatch(GameAction.SelectActionTarget(own))
        delay(FILM_BEAT_MS)
        g.session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(me, rank)))
    }
}

internal suspend fun queenFilm(vault: Vault): Filmed {
    val (game, _) = dealtAndPeeked(vault, MARKETING_SEED)
    return Filmed(game) { g ->
        val me = g.playerId
        // The seats either side of you, so the two cards cross the whole table.
        val players = g.session.view.value.players
        val left = players[1].id
        val right = players[players.size - 1].id
        drawAndUse(g, Rank.QUEEN)
        g.session.dispatch(GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, left, 0)))
        delay(FILM_BEAT_MS)
        g.session.dispatch(GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, right, 0)))
        delay(FILM_LOOK_MS)
        g.session.dispatch(GameAction.ExecuteQueenSwap(PlayerIdPayload(me)))
    }
}

/**
 * [coalitionGame]'s deal, stopped [FILM_LEAD] actions short of the call. Counted by playing it once
 * — the road is the same every time — so the live part starts where the caller's turn begins.
 */
internal suspend fun callFilm(vault: Vault): Filmed {
    suspend fun fresh(): LocalGame {
        val g = LocalGame.start(vault, COALITION_SEED, Difficulty.EASY, botDispatcher = Dispatchers.Default)
        val me = g.playerId
        g.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        g.session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        g.session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        return g
    }

    val rehearsal = fresh()
    var toTheCall = 0
    while (rehearsal.session.view.value.vintoCallerId == null && toTheCall < MAX_ACTIONS) {
        stepTowardTheCall(rehearsal)
        toTheCall++
    }
    check(rehearsal.session.view.value.vintoCallerId != null) { "no call in $MAX_ACTIONS moves" }

    val game = fresh()
    repeat((toTheCall - FILM_LEAD).coerceAtLeast(0)) { stepTowardTheCall(game) }
    return Filmed(game) { g ->
        delay(FILM_OPENING_MS)
        while (g.session.view.value.vintoCallerId == null) {
            stepTowardTheCall(g)
            delay(FILM_BEAT_MS)
        }
    }
}

/** How many of the table's actions the camera sees before the call: about the caller's turn. */
private const val FILM_LEAD = 4

/** Long enough for the screen to settle before anything moves. */
private const val FILM_OPENING_MS = 1_200L

/** Between the moves of one turn: time for the card to arrive before the next thing happens. */
private const val FILM_BEAT_MS = 1_100L

/** A Queen's look: both cards up long enough to read before they cross. */
private const val FILM_LOOK_MS = 1_800L
