package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A source map is the source, so no workflow publishes one (RELIABILITY.md, "A source map is the
 * source, so it is uploaded and then removed").
 *
 * The web build's dist holds `composeApp.<hash>.js.map` and a `sourceMappingURL` naming it until
 * `tools/strip-web-sourcemaps.mjs` takes both out. `deploy-web.yml` ran the strip from the start;
 * `preview-web.yml` builds the same dist and uploaded it as a preview version without it, so every
 * pull request's preview served the client's Kotlin sources (the bug
 * vinto/preview-serves-source-maps). Nothing ran a workflow to notice, so this reads them: every
 * workflow that builds the web client and hands it to wrangler for real strips the maps after
 * Sentry has them and before wrangler sees the dist, in a step with no condition on it.
 */
class WebSourceMapsStayUnpublishedTest {

    private val workflows = File("../.github/workflows")

    private fun publishers(): Map<String, List<String>> =
        workflows.listFiles { f -> f.name.endsWith(".yml") }.orEmpty()
            .associate { it.name to it.readLines() }
            .filterValues { lines ->
                lines.any { "wasmJsBrowserDistribution" in it } && lines.any(::publishes)
            }

    /** A wrangler call that sends something to Cloudflare: not `dev`, not a `--dry-run`. */
    private fun publishes(line: String) =
        Regex("""wrangler@\S*\s+(deploy|versions upload)""").containsMatchIn(line) && "--dry-run" !in line

    @Test
    fun bothWebPublishersAreFound() {
        // Without this the test below could pass by finding nothing to check.
        val found = publishers().keys
        assertTrue("deploy-web.yml" in found && "preview-web.yml" in found, "found only $found")
    }

    @Test
    fun aPreviewServesNoSourceMapsAndNeitherDoesTheSite() {
        for ((name, lines) in publishers()) {
            val strip = lines.indexOfFirst { "node tools/strip-web-sourcemaps.mjs" in it }
            assertTrue(strip >= 0, "$name publishes the web build and never strips its source maps")

            val upload = lines.indexOfFirst { "node tools/upload-web-sourcemaps.mjs" in it }
            assertTrue(upload in 0 until strip, "$name strips the maps before Sentry has them")

            val publish = lines.indexOfFirst(::publishes)
            assertTrue(strip < publish, "$name hands wrangler the dist before stripping its source maps")

            val step = lines.subList(0, strip).indexOfLast { it.trimStart().startsWith("- name:") }
            val condition = lines.subList(step, strip).firstOrNull { it.trimStart().startsWith("if:") }
            assertTrue(condition == null, "$name strips the source maps only `$condition`; it must be unconditional")
        }
    }
}
