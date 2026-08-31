package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A solo game reaches the network zero times.
 *
 * This is design R1's whole claim, and it is the kind of claim that quietly stops being true:
 * a session grows a leaderboard ping, a crash reporter, a "sync your streak" call, and nobody
 * notices because the game still plays. So it is not asserted by reading the code. A whole
 * round is played with the JVM's network calls **intercepted**, and any attempt at all — a
 * name lookup, a socket, an HTTP request — fails the test by throwing.
 *
 * The interception is a [SecurityManager], which JDK 17 deprecates and still honours; it is
 * the only hook at this JDK that catches every route out. `checkPermission` is deliberately
 * open so that nothing *else* is restricted — the test harness, file access and reflection
 * carry on untouched, and the only thing this manager has an opinion about is the network.
 *
 * A guard that is not actually installed passes silently, so the first thing the test does is
 * prove the guard bites.
 */
@Suppress("DEPRECATION") // SecurityManager is deprecated and is still the only hook that catches every route out.
class NoNetworkGuardTest {

    /** Thrown at anything trying to leave the machine. A `SecurityException` so the JDK lets it fly. */
    private class NetworkAttempted(what: String) :
        SecurityException("a solo game tried to reach the network: $what")

    private object DenyNetwork : SecurityManager() {
        /** Everything that is not the network is somebody else's business. */
        override fun checkPermission(perm: java.security.Permission) = Unit
        override fun checkPermission(perm: java.security.Permission, context: Any?) = Unit

        override fun checkConnect(host: String, port: Int) = throw NetworkAttempted("connect $host:$port")
        override fun checkConnect(host: String, port: Int, context: Any?) =
            throw NetworkAttempted("connect $host:$port")

        override fun checkListen(port: Int) = throw NetworkAttempted("listen on $port")
        override fun checkAccept(host: String, port: Int) = throw NetworkAttempted("accept $host:$port")
    }

    @Test
    fun aSoloGamePlaysAWholeRoundWithoutTouchingTheNetwork() {
        val previous = System.getSecurityManager()
        System.setSecurityManager(DenyNetwork)

        try {
            // --- the guard bites --------------------------------------------------------
            // Without this the rest of the test proves nothing: a manager that failed to
            // install would let a session phone home and still report success.
            assertFailsWith<NetworkAttempted>("name lookups are intercepted") {
                InetAddress.getByName("vinto.invalid")
            }
            assertFailsWith<NetworkAttempted>("sockets are intercepted") {
                Socket("vinto.invalid", HTTPS_PORT).close()
            }
            assertFailsWith<NetworkAttempted>("HTTP is intercepted") {
                URI("https://vinto.invalid/rooms").toURL().openStream().close()
            }

            // --- and the game does not ---------------------------------------------------
            val session = LocalGameSession(seed = 20260819L, difficulty = Difficulty.EASY)
            val person = BotRunner(Difficulty.EASY, Random(1L))

            val ended = mutableListOf<SessionEvent.RoundEnded>()

            runBlocking {
                val watching = launch {
                    session.events.collect { if (it is SessionEvent.RoundEnded) ended.add(it) }
                }
                // Let the collector reach its `collect` before the first move: a shared flow
                // buffers only for subscribers it already has.
                yield()

                var moves = 0
                while (!session.isOver && moves++ < MOVE_LIMIT) {
                    val action = person.nextAction(session.state.everySeatPlayable()) ?: break
                    if (session.dispatch(action) != null) break
                }
                yield()
                watching.cancel()
            }

            assertTrue(session.isOver, "the round finished")
            assertEquals(GamePhase.SCORING, session.view.value.phase)
            assertEquals(
                FOUR_SEATS,
                ended.singleOrNull()?.scores?.size,
                "and it scored — a round that never started would also have made no calls",
            )
        } finally {
            System.setSecurityManager(previous)
        }
    }

    private fun GameState.everySeatPlayable(): GameState =
        copy(players = players.map { it.copy(isHuman = false, isBot = true) })

    private companion object {
        const val HTTPS_PORT = 443
        const val FOUR_SEATS = 4
        const val MOVE_LIMIT = 400
    }
}
