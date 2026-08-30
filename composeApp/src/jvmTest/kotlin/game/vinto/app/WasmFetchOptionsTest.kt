package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The browser client never builds `fetch` options with `RequestInit(...)`.
 *
 * The generated constructor for `org.w3c.fetch.RequestInit` writes **every** member it knows
 * about, and the ones you did not pass are written as `null`. `cache` is a `RequestCache`
 * enum, and WebIDL accepts an *absent* member while refusing a null one — so the browser
 * rejects the call before it ever reaches the network:
 *
 *     TypeError: Failed to execute 'fetch' on 'Window': Failed to read the 'cache' property
 *     from 'RequestInit': The provided value 'null' is not a valid enum value of type
 *     RequestCache.
 *
 * What a player saw was "No connection to the room service. Check the network and try again."
 * — a network error, on a call that never touched the network, on the first day the site was
 * reachable. Every attempt to open, join or browse a room failed the same way, and only in the
 * browser: the Android, JVM and iOS connectors build their requests by other means entirely
 * and were fine.
 *
 * Nothing could have caught it earlier. It compiles, `kmp-web` compiles it, and the failure is
 * in the *shape of an object* handed to a browser API at runtime — which no Kotlin test can
 * see, because the type is an external interface and the value is legal Kotlin. So the guard is
 * on the source, in the mould of `PartialFunctionTest`: the construction that produces the bad
 * object may not appear at all.
 *
 * Build the options in JavaScript instead, with a one-expression `js(...)` function. An object
 * literal writes only the keys it names, so `cache` is absent and the browser applies its
 * default. `Beacon.wasmJs.kt` had been doing exactly this all along — for the unrelated reason
 * that `RequestInit` has no `keepalive` — which is why crash reports worked while the lobby
 * did not.
 */
class WasmFetchOptionsTest {

    private val sources = File("src/wasmJsMain/kotlin")

    /**
     * Kotlin with its comments removed.
     *
     * Load-bearing, and found by this test failing on its own first run: the KDoc above the
     * fix *names* `RequestInit(...)` and discusses `cache` at length, so a search of the raw
     * text finds the explanation rather than a call. `WebShellTest` hit the same trap reading
     * a page's commentary as content — a file that explains its own bug will match a search
     * for that bug.
     */
    private fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")

    @Test
    fun noBrowserRequestIsBuiltWithTheGeneratedConstructor() {
        assertTrue(sources.isDirectory, "${sources.path} moved; this test is stale")

        val offenders = sources.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                code(file.readText()).lines().withIndex().mapNotNull { (i, line) ->
                    // `: RequestInit = js(...)` is the fix, not the problem — only a call to
                    // the constructor is banned.
                    val call = Regex("""\bRequestInit\s*\(""").containsMatchIn(line)
                    if (call && !line.contains("js(")) "${file.name}: ${line.trim()}" else null
                }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "RequestInit(...) writes cache = null and the browser refuses the fetch. Build the " +
                "options with a js() object literal, as Beacon.wasmJs.kt does: " +
                offenders.joinToString("; "),
        )
    }

    /**
     * And the replacement really is an object literal that names only what it sets.
     *
     * A `js(...)` body that spread a Kotlin object, or that named `cache` explicitly, would
     * compile and fail in exactly the same way.
     */
    @Test
    fun theRoomCallsBuildTheirOptionsInJavaScript() {
        val net = File(sources, "game/vinto/app/net/Net.wasmJs.kt")
        assertTrue(net.exists(), "Net.wasmJs.kt moved; this test is stale")
        val body = code(net.readText())

        assertTrue(
            Regex("""fun \w+\([^)]*\): RequestInit = js\(""").containsMatchIn(body),
            "the browser connector no longer builds its fetch options in JavaScript",
        )
        assertTrue(
            !body.contains("cache"),
            "an explicit `cache` key is the thing the browser refused; leave it absent",
        )
    }
}
