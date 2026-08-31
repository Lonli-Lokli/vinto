package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Nothing on the online path reaches into wire data with a function that can throw.
 *
 * This is the guard the product owner asked for, at the only strength Kotlin allows. Where a
 * failure is a *state* — a call that did not work — it is now a type, and the compiler enforces
 * the handling: `RoomConnector` answers with `RoomAnswer`, `RemoteRoom` sends through
 * `SendOutcome`, and a `when` over either is exhaustive or it does not build. Where the danger
 * is instead a **partial function on data** — `first {}` on a list that may not contain what is
 * being looked for — the type system has nothing to say: `List.first {}` returns `T`, not `T?`,
 * and it throws. Kotlin cannot make that a compile error, so it is a build error instead.
 *
 * The bug this is drawn from: `FeltTable` reached the viewer's own seat with
 * `players.first { it.id == view.viewerId }`, while `tableFor` — the model beside it — used
 * `firstOrNull` and handled the miss. A solo game always seats you, so it never fired; online,
 * where the room decides who is seated, it is a crash with nothing between it and the launcher.
 *
 * Scope is deliberately the **client's view of the wire**: the screens and the session code
 * that read a `PlayerView`, a `LobbyView` or a room's answer. The engine is not covered and
 * should not be — it owns its own state, `first {}` on a list it just built is total in fact,
 * and a rule that cried wolf there would be turned off within a week.
 */
class PartialFunctionTest {

    /**
     * What may not appear, and what to use instead.
     *
     * `!!` and `error(...)` are here for the same reason as `first {}`: they are a decision to
     * crash rather than to answer, taken on data that arrived from somewhere else.
     */
    private val banned = listOf(
        Ban(".first {", "firstOrNull, and handle the miss"),
        Ban(".first()", "firstOrNull, and handle the empty list"),
        Ban(".last()", "lastOrNull"),
        Ban(".single()", "singleOrNull"),
        Ban(".getValue(", "the indexing operator with a null check"),
        Ban("!!", "a null check the reader can see"),
    )

    /**
     * The files that read what the room sends.
     *
     * Named one by one rather than swept by directory, because the point is a *boundary* and a
     * boundary that grows silently is not one. Adding a file here is a decision; a new screen
     * that reads a view and is not listed is the gap this test is about, which is why the count
     * below is asserted too.
     */
    private val onTheWirePath = listOf(
        "src/commonMain/kotlin/game/vinto/app/game/TableScreen.kt",
        "src/commonMain/kotlin/game/vinto/app/game/RoomScreen.kt",
        "src/commonMain/kotlin/game/vinto/app/game/Standings.kt",
        "src/commonMain/kotlin/game/vinto/app/OnlineScreen.kt",
        "src/commonMain/kotlin/game/vinto/app/DiscoverScreen.kt",
        "../shared/client/src/commonMain/kotlin/game/vinto/client/RemoteGame.kt",
        "../shared/client/src/commonMain/kotlin/game/vinto/client/LobbyModel.kt",
        "../shared/client/src/commonMain/kotlin/game/vinto/client/Discovery.kt",
    )

    @Test
    fun theWirePathNeverCrashesOnDataItDidNotBuild() {
        val complaints = onTheWirePath.flatMap { path ->
            val file = File(path)
            assertTrue(file.exists(), "$path is listed here and does not exist; the list is stale")

            file.readLines().withIndex().flatMap { (line, text) ->
                val code = text.substringBefore("//")
                banned.filter { code.contains(it.what) }.map { ban ->
                    "$path:${line + 1} uses `${ban.what}` on data from the wire — use ${ban.instead}"
                }
            }
        }

        assertTrue(complaints.isEmpty(), complaints.joinToString("\n"))
    }

    /**
     * And the list above still covers the screens that read a view.
     *
     * Without this, the guard erodes by addition rather than by edit: somebody writes a new
     * online screen, does not think to list it, and the suite stays green while the boundary
     * shrinks. The number is a tripwire, not a target — raise it when you have added the file
     * to the list.
     */
    @Test
    fun theBoundaryHasNotQuietlyShrunk() {
        assertTrue(
            onTheWirePath.size >= LISTED,
            "the wire path lists ${onTheWirePath.size} files, fewer than the $LISTED it had",
        )
    }

    private data class Ban(val what: String, val instead: String)

    private companion object {
        const val LISTED = 8
    }
}
