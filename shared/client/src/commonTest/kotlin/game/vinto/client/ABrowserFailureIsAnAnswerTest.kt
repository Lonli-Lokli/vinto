package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * What a browser throws is an answer, like what any other platform throws.
 *
 * On Kotlin/Wasm a JavaScript exception arrives as `JsException`, and `JsException` extends
 * `Throwable`, not `Exception`. So `answering { }` — which catches `Exception` — let it through,
 * and the coroutine that asked failed instead of reading `Failed`. Reproduced in a local build of
 * the web client: with `fetch` throwing where it is called, "Browse public rooms" crashed its
 * coroutine and the screen stopped answering, instead of saying the room service was unreachable.
 *
 * [BrowserFailure] is that shape on every target: a `Throwable` that is not an `Exception`.
 */
class ABrowserFailureIsAnAnswerTest {

    @Test
    fun aBrowserFailureIsAnAnswerNotACrash() = runTest {
        val answer = answering<Unit> { throw BrowserFailure("TypeError: Failed to fetch") }

        assertEquals(RoomTrouble.OFFLINE, assertIs<RoomAnswer.Failed>(answer).trouble)
    }

    /** The machine failing is not the network failing, and is not dressed up as one. */
    @Test
    fun theMachineFailingIsStillAFailure() = runTest {
        assertFailsWith<MachineFailure> { answering<Unit> { throw MachineFailure() } }
    }

    private class MachineFailure : Error("out of memory")
}

/** What Kotlin/Wasm makes of a JavaScript exception: thrown, and not an `Exception`. */
internal class BrowserFailure(message: String) : Throwable(message)
