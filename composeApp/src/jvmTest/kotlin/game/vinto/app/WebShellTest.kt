package game.vinto.app

import game.vinto.app.link.INVITE_HOST
import game.vinto.app.link.INVITE_PATH
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page a link to this game unfurls into, and the files around it.
 *
 * Nothing else in this repository looks at `wasmJsMain/resources` at all. `kmp-web` *compiles*
 * the web client, `kmp-android` and `kmp-ios` build the other two, and no job has ever opened
 * a browser — which is how the branch went its whole life with no `index.html` whatsoever
 * (docs/kotlin/README.md §7) and then with an `index.html` referencing a `favicon.png` that
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

        assertTrue(
            redirects.contains("$INVITE_PATH* /index.html 200"),
            "no SPA fallback for $INVITE_PATH — an invitation link 404s",
        )
        // Line by line, not by substring: the scoped rule *ends with* the blanket one, so
        // `contains` reports the very thing this is here to allow.
        assertTrue(
            redirects.lineSequence().none { it.trim().startsWith("/* ") },
            "a blanket SPA fallback answers a missing asset with HTML; see README section 6c",
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
     * handed a web page and told it succeeded, and §6c's cache rules keep that answer.
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
        // shell and a 200 — measured on the Pages deployment, and the trap README section 6c
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
}
