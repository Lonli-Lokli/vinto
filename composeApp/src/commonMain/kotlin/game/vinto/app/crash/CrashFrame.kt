package game.vinto.app.crash

/**
 * One line of a stack trace, taken apart into the fields Sentry actually reads.
 *
 * ## Why this exists
 *
 * The envelope used to send each line as `{"filename": "<the whole raw line>"}` and nothing else.
 * Sentry has no way to read that, so every frame of the first real report came back as
 *
 *     Unknown function in at 5   Vinto.debug.dylib   0x1073b3077
 *     kfun:game.vinto.app.game.Verdict#internal + 7475  [Line: Unknown]  (Not in app)
 *
 * Three things were lost, and each costs something different:
 *
 *   * **No `function`.** Sentry names an issue after the topmost in-app frame, so with none it
 *     used the deepest frame it had — `kotlin.Throwable#<init>` — as the culprit of every crash
 *     this app will ever report. Two unrelated bugs would look like the same issue.
 *   * **No `in_app`.** Every frame reads "(Not in app)", so the whole of Kotlin's and Compose's
 *     internals rank equally with ours and nothing is highlighted.
 *   * **No `instruction_addr`.** This is the one that makes uploaded symbols useless: Sentry
 *     symbolicates a native frame by looking its ADDRESS up in a dSYM. Sending dSYMs while the
 *     event carries no addresses changes nothing at all, which is worth knowing before anybody
 *     spends an afternoon on the upload half.
 *
 * ## The two shapes
 *
 * `Throwable.stackTraceToString()` produces a different format per target, and both reach this
 * code from the same `commonMain` reporter:
 *
 *     JVM/Android   at game.vinto.app.game.Verdict.invoke(Standings.kt:171)
 *     Kotlin/Native at 5   Vinto.debug.dylib   0x1073b3077   kfun:game.vinto.app.game.Verdict#internal + 7475
 *
 * Anything else — Kotlin/JS, a future target, a line the format changes under — falls through to
 * [Unparsed], which keeps the old behaviour for that line rather than dropping it. A stack trace
 * that is merely hard to read beats one with a hole in it.
 */
internal sealed interface CrashFrame {

    /** `at pkg.Class.method(File.kt:42)` — a JVM or Android frame. */
    data class Jvm(
        val function: String,
        val file: String,
        val line: Int?,
    ) : CrashFrame

    /** `at 5  Image  0xADDR  kfun:pkg.fn + 123` — a Kotlin/Native frame. */
    data class Native(
        val function: String,
        val image: String,
        val address: String,
    ) : CrashFrame

    /** A line neither pattern claims. Sent as it always was, so nothing is lost. */
    data class Unparsed(val raw: String) : CrashFrame
}

/**
 * `at pkg.Class.method(File.kt:42)`, with the line number optional — a stack from a JAR has
 * `(Unknown Source)` where one from a debug build has a file and a line.
 */
private val jvmFrame = Regex("""^at\s+([\w.$]+)\((.+?)(?::(\d+))?\)$""")

/**
 * `at 5   Vinto.debug.dylib   0x1073b3077   kfun:… + 7475`.
 *
 * The trailing `+ 7475` is the offset into the function and is dropped: the absolute address
 * before it is what a dSYM lookup needs, and the offset is already implied by it.
 */
private val nativeFrame = Regex("""^at\s+\d+\s+(\S+)\s+(0x[0-9a-fA-F]+)\s+(.+?)(?:\s+\+\s+\d+)?$""")

/** Takes one trimmed stack line apart, or gives up honestly. */
internal fun parseFrame(raw: String): CrashFrame {
    jvmFrame.matchEntire(raw)?.let { m ->
        return CrashFrame.Jvm(
            function = m.groupValues[1],
            file = m.groupValues[2],
            line = m.groupValues[3].toIntOrNull(),
        )
    }
    nativeFrame.matchEntire(raw)?.let { m ->
        return CrashFrame.Native(
            function = m.groupValues[3],
            image = m.groupValues[1],
            address = m.groupValues[2],
        )
    }
    return CrashFrame.Unparsed(raw)
}

/**
 * Whether a frame is ours, which is what Sentry uses to name the issue and to fold the rest away.
 *
 * Matched on the package because that is the one thing both formats carry: `game.vinto.app.…` on
 * the JVM and `kfun:game.vinto.app.…` on Native. Deliberately NOT the binary name — on iOS every
 * frame including Kotlin's own runtime and Compose sits in `Vinto.debug.dylib`, so trusting the
 * image would mark the entire stack as ours and highlight nothing.
 */
internal fun CrashFrame.isOurs(): Boolean = when (this) {
    is CrashFrame.Jvm -> function.startsWith(OUR_PACKAGE)
    is CrashFrame.Native -> function.contains(OUR_PACKAGE)
    is CrashFrame.Unparsed -> raw.contains(OUR_PACKAGE)
}

private const val OUR_PACKAGE = "game.vinto."
