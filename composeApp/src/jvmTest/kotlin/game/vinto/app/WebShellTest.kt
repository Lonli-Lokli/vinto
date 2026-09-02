package game.vinto.app

import game.vinto.app.link.INVITE_HOST
import game.vinto.app.link.INVITE_PATH
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The page a link to this game unfurls into, and the files around it.
 *
 * Nothing else in this repository looks at `wasmJsMain/resources` at all. `kmp-web` *compiles*
 * the web client, `kmp-android` and `kmp-ios` build the other two, and no job has ever opened
 * a browser — which is how the branch went its whole life with no `index.html` whatsoever
 * (docs/kotlin/TRAPS.md §7) and then with an `index.html` referencing a `favicon.png` that
 * was not there. A compile gate is not a serve gate, and this is the serve gate's stand-in:
 * the cheapest possible check, that every file the shell names is a file that exists and that
 * the tags a crawler reads say the same thing as each other.
 *
 * None of it asserts wording. What a page says is copy and changes; what is checked here is
 * that it is *present*, *absolute* where it has to be, and *consistent* — the three ways an
 * unfurl silently shows nothing, which is a failure with no error message anywhere and no way
 * to notice except by posting a link and looking at it.
 */
class WebShellTest {

    private val dir = File("src/wasmJsMain/resources")

    private fun read(name: String): String {
        val file = File(dir, name)
        assertTrue(file.exists(), "$name is missing from ${dir.path}")
        return file.readText()
    }

    /** `<meta ... content="...">` and `<meta content="..." ...>`, either order. */
    private fun meta(html: String, key: String): String? {
        val tag = Regex("""<meta\b[^>]*\b(?:name|property)="${Regex.escape(key)}"[^>]*>""")
            .find(html)?.value ?: return null
        return Regex("""\bcontent="([^"]*)"""").find(tag)?.groupValues?.get(1)
    }

    /** The page without its `<!-- ... -->`, which explain themselves at some length. */
    private fun withoutComments(html: String) = html.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

    /** A `#`-commented config file without its commentary. */
    private fun uncommented(text: String) =
        text.lineSequence().filter { !it.trimStart().startsWith("#") }.joinToString("\n")

    private fun link(html: String, rel: String): String? {
        val tag = Regex("""<link\b[^>]*\brel="${Regex.escape(rel)}"[^>]*>""")
            .find(html)?.value ?: return null
        return Regex("""\bhref="([^"]*)"""").find(tag)?.groupValues?.get(1)
    }

    /**
     * Every tag a link preview reads is present.
     *
     * The list is the intersection of what Facebook, Slack, Discord, iMessage and X actually
     * look at. A missing one is not an error anywhere — the crawler simply falls back to
     * whatever it can scrape, which for a page whose body is emptied by Compose is nothing.
     */
    @Test
    fun aLinkToThisPageHasSomethingToUnfurl() {
        val html = read("index.html")

        listOf(
            "description",
            "og:type",
            "og:site_name",
            "og:url",
            "og:title",
            "og:description",
            "og:image",
            "og:image:width",
            "og:image:height",
            "og:image:alt",
            "twitter:card",
            "twitter:title",
            "twitter:description",
            "twitter:image",
        ).forEach { key ->
            val value = meta(html, key)
            assertTrue(!value.isNullOrBlank(), "no $key on the page, so a preview falls back to nothing")
        }

        assertTrue(
            Regex("""<title>[^<]+</title>""").containsMatchIn(html),
            "no <title>, which is the one thing every crawler reads",
        )
        assertEquals(
            "summary_large_image",
            meta(html, "twitter:card"),
            "a 1200x630 card cropped to a square thumbnail wastes the picture",
        )
    }

    /**
     * And the image URLs are absolute.
     *
     * This is the single most common way an unfurl shows nothing: a crawler fetching the tag
     * has no page context to resolve a relative path against, so it drops the tag rather than
     * guessing. It looks completely correct in a browser, which is what makes it worth a test
     * rather than a review.
     */
    @Test
    fun theShareImageIsSomewhereACrawlerCanFetchIt() {
        val html = read("index.html")

        listOf("og:image", "twitter:image").forEach { key ->
            val url = meta(html, key).orEmpty()
            assertTrue(
                url.startsWith("https://"),
                "$key is \"$url\" — a relative og:image is dropped, not resolved",
            )
        }
        assertTrue(
            link(html, "canonical").orEmpty().startsWith("https://"),
            "the canonical link has to be absolute to mean anything",
        )
    }

    /**
     * The three descriptions are one description.
     *
     * They are separate tags read by separate consumers, so nothing makes them agree except
     * somebody remembering to edit all three. This is what happens instead of remembering.
     */
    @Test
    fun oneDescriptionRatherThanThree() {
        val html = read("index.html")
        val plain = meta(html, "description")

        assertEquals(plain, meta(html, "og:description"), "og:description has drifted")
        assertEquals(plain, meta(html, "twitter:description"), "twitter:description has drifted")
        assertEquals(meta(html, "og:title"), meta(html, "twitter:title"), "the titles have drifted")
    }

    /**
     * Every relative URL on the page resolves against the site root.
     *
     * **The check that was missing when every invitation was broken.** The shell is served at
     * `/r/ABC123` with a 200 so the address bar keeps the code, which means a
     * relative script src asks for `/r/composeApp.js` — and the same SPA fallback answers
     * that with the shell itself, 200, `text/html`. The browser loads an HTML document as a
     * classic script, dies on `Unexpected token '<'`, and the page sits on its loading line
     * for ever. Every link anybody shared did this; a link to `/` did not, which is why it
     * survived a deploy, a probe of the live site, and seven cases in this file.
     *
     * The fix is `<base href="/">`, and it is asserted rather than the individual tags because
     * it is the only thing that also reaches what the wasm bundle fetches for itself at
     * runtime — which is not in this file at all and cannot be checked by reading it.
     */
    @Test
    fun everyRelativeUrlResolvesAgainstTheRoot() {
        val shell = read("index.html")
        assertTrue(
            Regex("""<base\s+href="/">""").containsMatchIn(shell),
            "no <base href=\"/\"> — the shell works at / and breaks at $INVITE_PATH*, which is " +
                "every link anybody shares",
        )
    }

    /**
     * Every local file the shell names is a file that is there.
     *
     * On Pages a path that does not exist is not a 404: `_redirects` answers everything under
     * `/r/` with the
     * shell, and a mistyped asset elsewhere is a plain 404 that at least says so. Either way
     * a browser asking for an icon and receiving something else is a bug found by a person
     * looking at a tab, which is a long way round. The `favicon.png` this page referenced was
     * missing for exactly as long as the page had existed.
     */
    @Test
    fun everyFileTheShellNamesExists() {
        val html = read("index.html")

        // Comments first. This page explains itself at some length, and one of those comments
        // quotes a `src="..."` to say that the deploy step rewrites it — which a scan for
        // asset references reads as a reference to a file called `...`.
        val named = Regex("""(?:href|src)="([^":]+)"""").findAll(withoutComments(html))
            .map { it.groupValues[1] }
            .filter { !it.startsWith("//") && !it.startsWith("#") }
            .map { it.trimStart('/') }
            .toSet() + setOf("share-card.png", "robots.txt", "sitemap.xml", "_headers", "_redirects")

        named.forEach { name ->
            // The script's name is rewritten at deploy time; the file it starts life as is
            // produced by webpack rather than committed, so it is the one exception.
            if (name == "composeApp.js") return@forEach
            assertTrue(File(dir, name).exists(), "the shell names $name and there is no such file")
        }
    }

    /** The share card is the size every preview crops to, and says so in its own tags. */
    @Test
    fun theShareCardIsTheSizeItClaims() {
        val html = read("index.html")
        val png = File(dir, "share-card.png")

        // PNG: an 8-byte signature, then an IHDR chunk whose payload starts at byte 16 with
        // width and height as big-endian 32-bit integers. Cheaper than an image library for
        // the only two numbers that matter here.
        val bytes = png.readBytes()
        fun intAt(offset: Int) = (0..3).fold(0) { acc, i -> (acc shl 8) or (bytes[offset + i].toInt() and 0xFF) }

        assertEquals(1200, intAt(16), "share-card.png is not 1200 wide")
        assertEquals(630, intAt(20), "share-card.png is not 630 tall")
        assertEquals("1200", meta(html, "og:image:width"))
        assertEquals("630", meta(html, "og:image:height"))
    }

    /**
     * The routing file agrees with the client about where an invitation lives.
     *
     * `Main.kt` reads `window.location.pathname` and hands it to `roomCodeFrom`, so
     * `/r/7KQ2MP` has to reach the shell rather than a 404 — and `INVITE_PATH` is the
     * constant that decides which prefix that is. If somebody shortens it, the redirect and
     * the robots rule both quietly stop matching and invitations 404 in production only.
     */
    @Test
    fun anInvitationLinkIsRoutedToTheApp() {
        // The comments in both files quote the rules they are about, including the blanket
        // fallback this asserts is absent, so the rules have to be read on their own.
        val redirects = uncommented(read("_redirects"))
        val robots = uncommented(read("robots.txt"))

        // The target is `/`, not `/index.html`. Workers static assets **validates** this file
        // where Pages did not, and rejects `/index.html` with "Infinite loop detected in this
        // rule" — its default `html_handling` strips `/index` and `.html`, so the rewrite
        // target re-enters the rule. That cost a whole deploy to find out, after the build,
        // the hashing and the upload had all succeeded.
        assertTrue(
            redirects.lineSequence().any { it.trim() == "$INVITE_PATH* / 200" },
            "no rewrite for $INVITE_PATH — an invitation link 404s",
        )
        assertTrue(
            redirects.lineSequence().none { it.contains("/index.html") },
            "a rewrite target of /index.html is refused by Workers as an infinite loop; use /",
        )
        // 200 is a rewrite, so the address bar keeps `/r/<code>` and `Main.kt` can still read
        // the code out of `window.location.pathname`. A 301 or 302 would lose it.
        val redirected = Regex(" 30[128]\\s*$")
        assertTrue(
            redirects.lineSequence()
                .filter { it.trim().startsWith(INVITE_PATH) }
                .none { redirected.containsMatchIn(it) },
            "an invitation must be rewritten, not redirected — a redirect changes the URL and drops the code",
        )
        // Line by line, not by substring: the scoped rule *ends with* the blanket one, so
        // `contains` reports the very thing this is here to allow.
        assertTrue(
            redirects.lineSequence().none { it.trim().startsWith("/* ") },
            "a blanket SPA fallback answers a missing asset with HTML; see HOSTING.md §6c",
        )
        assertTrue(
            robots.contains("Disallow: $INVITE_PATH"),
            "room codes are indexable: somebody's invitation becomes a search result that outlives the room",
        )
        assertTrue(
            read("index.html").contains(INVITE_HOST) && read("sitemap.xml").contains(INVITE_HOST),
            "the page and the sitemap name a different host from the invitations ($INVITE_HOST)",
        )
    }

    /**
     * A path that is not there answers 404, which takes a file to arrange.
     *
     * Cloudflare Pages falls back to `index.html` — **200**, `text/html` — for any unmatched
     * path unless a `404.html` is present. Scoping `_redirects` to `/r/` was not enough,
     * because that fallback is Pages' own default rather than something the redirect rules
     * were causing: measured against the live deployment, `/no-such-file.js` came back
     * `200 text/html; charset=utf-8`. So a browser asking for a script it cannot have is
     * handed a web page and told it succeeded, and HOSTING.md §6c's cache rules keep that answer.
     *
     * The file is the whole fix, which is exactly why it is worth a test — nothing about
     * deleting it would look wrong.
     */
    @Test
    fun aMissingPathHasSomethingToAnswerWith() {
        val notFound = read("404.html")

        assertTrue(
            notFound.contains("noindex"),
            "the 404 page is indexable, so it can turn up in search results as if it were content",
        )
        // No script and no wasm: a 404 page that depends on the bundle breaks in exactly the
        // situation it exists for.
        assertTrue(
            !notFound.contains("<script"),
            "the 404 page loads a script — it has to stand up when the bundle is what is missing",
        )
        assertTrue(
            read("_redirects").lineSequence().any { it.trim().startsWith(INVITE_PATH) },
            "with a 404.html present, only _redirects keeps invitation links reaching the app",
        )
    }

    /**
     * The hosting config claims the host the app tells people to visit.
     *
     * The site is a Worker serving static assets rather than a Pages project, for one
     * reason: a Pages custom domain can only be attached in the dashboard, so the hostname
     * stayed behind "somebody has to open a browser" on a project whose deploy is otherwise
     * a button in a phone app. A Worker claims its hostname from `routes`.
     *
     * Which makes that line load-bearing in a way nothing else checks. `INVITE_HOST` is what
     * every invitation link says; the route is what actually answers. If they drift, every
     * invitation points at a host nothing serves — and both files look completely fine on
     * their own.
     */
    @Test
    fun theSiteIsServedAtTheHostInvitationsName() {
        val config = File("cloudflare/wrangler.jsonc")
        assertTrue(config.exists(), "the web Worker's config moved; this test is stale")
        val text = config.readText()

        assertTrue(
            text.contains("\"pattern\": \"$INVITE_HOST\""),
            "the deploy claims a different host from the one invitations name ($INVITE_HOST)",
        )
        assertTrue(
            text.contains("\"custom_domain\": true"),
            "without custom_domain the route does not create the DNS record, which is the whole reason this is a Worker",
        )
        // The other option, "single-page-application", answers every unmatched path with the
        // shell and a 200 — measured on the Pages deployment, and the trap HOSTING.md §6c
        // is about. `404.html` only does its job under this setting.
        assertTrue(
            text.contains("\"not_found_handling\": \"404-page\""),
            "not_found_handling is not 404-page, so a missing asset is answered with the shell and a 200",
        )
    }

    /** And the installable manifest points at icons that are really there. */
    @Test
    fun theManifestNamesIconsThatExist() {
        val manifest = read("manifest.webmanifest")

        val icons = Regex(""""src"\s*:\s*"/([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue(icons.isNotEmpty(), "the manifest lists no icons at all")
        icons.forEach { name ->
            assertTrue(File(dir, name).exists(), "the manifest names $name and there is no such file")
        }
        assertTrue(
            manifest.contains("\"purpose\": \"maskable\""),
            "no maskable icon: an installed app gets the mark cropped by whatever shape the launcher likes",
        )
    }

    /**
     * `immutable` is a promise only a content-addressed URL can keep, and one rule broke it.
     *
     * This is the regression test for the bug that shipped: everything under `composeResources`
     * was served
     * `immutable, max-age=31536000` under fixed names. Compose reads strings by **byte offset**
     * into the per-locale `strings.commonMain.cvr`, and those offsets live in the wasm — which is
     * content-hashed, so it is always the current build's. A returning visitor therefore paired
     * new offsets with a year-old table and read every string after the first changed entry from
     * the wrong place: truncated mid-word, decoded as mojibake, or empty. The home screen was
     * fine and everything behind "Play online" was not, because those keys sort later.
     *
     * So the rule is stated as a rule rather than as a fixed string: a path may be `immutable`
     * only if it carries a content hash. The two wasm and script rules do; nothing else does.
     */
    @Test
    fun onlyContentAddressedPathsAreImmutable() {
        val contentAddressed = setOf("/*.wasm", "/composeApp.*.js")

        var path = ""
        uncommented(read("_headers")).lineSequence().forEach { line ->
            if (line.startsWith("/")) {
                path = line.trim()
            } else if (line.contains("immutable")) {
                assertTrue(
                    contentAddressed.contains(path),
                    "$path is served immutable and carries no content hash, so a stale copy is served for a year",
                )
            }
        }
    }

    /**
     * Every locale's string table is un-cacheable, and every one is repaired on the way in.
     *
     * Two halves of the same fix, checked together because either alone leaves the bug: the
     * `_headers` rule stops it happening to anyone new, and the `index.html` refetch undoes it
     * for the browsers that already hold a poisoned copy — which will not otherwise ask about
     * that file again for a year, `immutable` being a promise not to.
     *
     * The reason this is a test rather than a comment is that both halves name their locales
     * literally, and adding `values-be/` is meant to be a file and no code (WORDS.md §6h).
     * A new locale with no line in either place is a silent return of the same corruption, in
     * that language only.
     */
    @Test
    fun everyStringTableIsRefetchedAndRepaired() {
        val resources = File("src/commonMain/composeResources")
        val locales = resources.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { it.name }
            .sorted()
        assertTrue(locales.isNotEmpty(), "no values* directories found under ${resources.path}")

        // The package the resource paths are built from, read rather than repeated.
        val gradle = File("build.gradle.kts").readText()
        val pkg = assertNotNull(
            Regex("""packageOfResClass\s*=\s*"([^"]+)"""").find(gradle)?.groupValues?.get(1),
            "packageOfResClass is not set in composeApp/build.gradle.kts",
        )

        val headers = uncommented(read("_headers"))
        val shell = read("index.html")
        val repairList = assertNotNull(
            Regex("""var LOCALES = \[([^\]]*)\]""").find(shell)?.groupValues?.get(1),
            "index.html no longer carries the composeResources repair",
        )

        locales.forEach { locale ->
            val table = "/composeResources/$pkg/$locale/strings.commonMain.cvr"
            val rule = headers.lineSequence()
                .dropWhile { it.trim() != table }
                .drop(1)
                .firstOrNull { it.isNotBlank() }
            assertEquals(
                "Cache-Control: no-store",
                rule?.trim(),
                "$table is not no-store, so a stale copy can be paired with this build's offsets",
            )
            assertTrue(
                repairList.contains("\"$locale\""),
                "index.html does not refetch $locale, so a browser already holding a stale copy keeps it",
            )
        }

        assertTrue(
            shell.contains("\"$pkg\""),
            "the repair in index.html builds its paths from a different package than packageOfResClass ($pkg)",
        )
    }

    /**
     * The page says whose game this is, and links to it, without running a byte of wasm.
     *
     * This is the one frame two visitors ever see — a crawler that will not execute four
     * megabytes of WebAssembly, and somebody whose browser has no WasmGC — and both of them
     * are exactly the readers who cannot be shown the app's own credit line. So the shell
     * carries it too: the word, the address, and a real anchor rather than a sentence about
     * one, because a link a person cannot follow is a citation and not an attribution.
     *
     * The wordmark is separately the way to this app's page on the studio's site, which is
     * the closest thing this canvas-only client has to a logo somebody can click.
     */
    @Test
    fun theShellSaysThisIsAnUnofficialAppAndLinksToTheGame() {
        val shell = withoutComments(read("index.html"))

        assertTrue(
            shell.contains("unofficial", ignoreCase = true),
            "the pre-boot content does not say the app is unofficial",
        )
        assertTrue(
            shell.contains("""<a href="${Pages.OFFICIAL}">"""),
            "the pre-boot content has no link to the game itself (${Pages.OFFICIAL})",
        )
        assertTrue(
            shell.contains("""<h1><a href="${Pages.THIS_APP}">"""),
            "the wordmark does not open this app's own page (${Pages.THIS_APP})",
        )

        // And a machine reading the page is told the same thing, in the vocabulary it has for
        // it: a rich result that presents this as the original would be the same claim the
        // copy is careful not to make.
        val json = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(shell)
            ?.groupValues
            ?.get(1)
        assertTrue(json != null && json.contains(Pages.OFFICIAL), "the structured data does not name the original")
    }
}
