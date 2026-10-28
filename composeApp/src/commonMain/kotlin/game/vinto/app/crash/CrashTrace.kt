package game.vinto.app.crash

/**
 * What a failure says about itself, read so that one which cannot say it is still reported.
 *
 * Every read here can throw, and on the web two of them do. A browser error that carries no
 * `stack` — Chromium's `DOMException` is one, and it is what a refused `localStorage` throws —
 * reaches Kotlin/Wasm as a `JsException` whose `stackTraceToString()` throws
 * `NullPointerException`. The reporter used to call it unguarded from inside a
 * `CoroutineExceptionHandler`, and a handler that throws is what kotlinx.coroutines files as
 * "Exception while trying to handle coroutine exception": the failure it was handed, buried under
 * the reporter's own (Sentry 7817278407).
 */
internal data class Account(val type: String, val message: String, val trace: String)

internal fun accountOf(error: Throwable): Account =
    if (error is RelayedCrash) {
        Account(error.type, error.message, error.trace)
    } else {
        Account(
            type = error::class.simpleName ?: "Throwable",
            message = runCatching { error.message }.getOrNull() ?: NO_MESSAGE,
            // A header and no frames, rather than no report.
            trace = runCatching { error.stackTraceToString() }.getOrNull() ?: "",
        )
    }

/**
 * A failure that reached the app as words — a browser's error event — rather than as a
 * [Throwable] it can walk.
 *
 * The browser hands over a name, a message and a stack, and the stack is the part worth having:
 * when kotlinx.coroutines gives up on a failure it rebuilds it as a JavaScript `Error` whose
 * `stack` is the whole of `stackTraceToString()`, `Caused by:` and `Suppressed:` included. The
 * listener used to keep the event's one-line message and then report a Throwable of its own, so
 * every web report carried that line and the frames of its own constructor — eight frames that
 * said where the reporter was, and nothing about the failure.
 */
internal class RelayedCrash(
    val type: String,
    override val message: String,
    /** Always starts with a header line, so the first frame is never taken for one. */
    val trace: String,
) : Throwable(message)

/**
 * Builds a [RelayedCrash] from whatever the browser had, any of which may be empty.
 *
 * A thrown string or a cross-origin "Script error." has no name and no stack; a stack from
 * Firefox or Safari starts with a frame, not with the error's name. So a header is written in
 * front of any stack that does not already begin with one.
 */
internal fun relayedCrash(name: String, message: String, stack: String, fallback: String): RelayedCrash {
    val type = name.ifBlank { RELAYED }
    val said = message.ifBlank { fallback }
    val header = "$type: $said"
    val trace = when {
        stack.isBlank() -> header
        stack.lineSequence().first().contains(type) -> stack
        else -> "$header\n$stack"
    }
    return RelayedCrash(type, said, trace)
}

/** One exception in a printed trace: its own header and frames, without its causes'. */
data class ChainedException(val type: String, val message: String, val frames: List<String>)

/**
 * A printed trace taken apart at every `Caused by:` and `Suppressed:`, the failure itself first.
 *
 * The first line is the failure's header and is dropped. Two kinds of line are not frames and are
 * dropped too: an elision (`... 5 more` on the JVM, `... and 3 more common stack frames skipped`
 * on the web), and the header said a second time. Kotlin/Wasm prints the JavaScript stack under
 * its own header, and that stack opens with the engine's own `IllegalStateException: …` — so every
 * web report's newest frame was a sentence. Anything else unrecognised stays, as it always has.
 */
internal fun traceSections(trace: String): List<ChainedException> {
    val lines = trace.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val sections = mutableListOf(Section(lines.firstOrNull().orEmpty()))
    for (line in lines.drop(1)) {
        val opens = OPENERS.firstOrNull { line.startsWith(it) }
        val current = sections.last()
        when {
            opens != null -> sections += Section(line.removePrefix(opens))
            line.startsWith(ELISION) -> Unit
            current.frames.isEmpty() && current.repeats(line) -> Unit
            else -> current.frames += line
        }
    }
    return sections.filterNot { it.circular }.map { it.toChained() }
}

private class Section(header: String) {
    val circular = header.startsWith(CIRCULAR)
    val type = header.substringBefore(": ").substringAfterLast('.')
    val message = header.substringAfter(": ", missingDelimiterValue = NO_MESSAGE)
    val frames = mutableListOf<String>()

    fun repeats(line: String): Boolean =
        !line.startsWith(FRAME) && (line == type || line.startsWith("$type: "))

    fun toChained() = ChainedException(type, message, frames.toList())
}

private const val NO_MESSAGE = "no message"

/** What a relayed failure is called when the browser gave it no name. */
private const val RELAYED = "BrowserCrash"

private val OPENERS = listOf("Caused by: ", "Suppressed: ")
private const val ELISION = "..."
private const val FRAME = "at "
private const val CIRCULAR = "[CIRCULAR REFERENCE"
