package game.vinto.engine

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The corpus is frozen, and this is what makes that a rule rather than a preference.
 *
 * `fixtures/recordings` holds 50 games and 13,900 actions, each carrying the canonical state
 * hash **TypeScript computed**. That is the whole value of it: an independent implementation,
 * written from the rules rather than from this code, agreed on every one of those numbers. The
 * TypeScript engine is gone, so the corpus can never be regenerated — and a regenerated corpus
 * would be worth strictly less than none at all, because it would look identical while proving
 * only that the Kotlin engine agrees with itself.
 *
 * So the failure this guards against is not malice. It is somebody in a year's time, facing a
 * red `CorpusReplayTest` after a deliberate rules change, reaching for the obvious fix: write
 * the fixtures out again from the current engine. Every test goes green, and the branch that
 * proved two implementations agreed is quietly gone. Nothing else in the build would notice.
 *
 * `MANIFEST.sha256` is the record. If a recording changes, this fails and names it, and the
 * only correct responses are to revert the change or to delete this test in a commit that
 * argues for it — which is a conversation, and that is the point.
 *
 * **Adding coverage is a different thing and is allowed.** A Kotlin-generated recording goes in
 * `fixtures/kotlin-recordings/`, which has its own README about what it can and cannot prove.
 * This test does not look there.
 */
class CorpusIsFrozenTest {

    private val dir =
        File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun everyRecordingIsTheOneTypeScriptWrote() {
        val manifest = File(dir, "MANIFEST.sha256")
        assertTrue(
            manifest.exists(),
            "no MANIFEST.sha256 in ${dir.absolutePath} — the freeze has nothing to check against",
        )

        val recorded = manifest.readLines()
            .filter { it.isNotBlank() }
            .associate { line ->
                val (hash, name) = line.split("  ", limit = 2)
                name to hash
            }

        val onDisk = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

        // Named individually rather than by count, so a failure says which file and not just
        // that a number moved.
        assertEquals(
            recorded.keys.sorted(),
            onDisk.map { it.name },
            "the corpus gained or lost a recording; it is frozen (see fixtures/recordings/README.md)",
        )

        onDisk.forEach { file ->
            assertEquals(
                recorded.getValue(file.name),
                sha256(file),
                "${file.name} has changed. The corpus carries hashes TypeScript computed and " +
                    "cannot be regenerated — see fixtures/recordings/README.md before touching this",
            )
        }
    }

    /**
     * And the manifest is not empty, which is the way this test fails open.
     *
     * A manifest emptied by an over-eager script would make every assertion above vacuous:
     * no files recorded, none expected, nothing compared. Fifty is what TypeScript produced.
     */
    @Test
    fun theManifestStillDescribesTheWholeCorpus() {
        val lines = File(dir, "MANIFEST.sha256").readLines().filter { it.isNotBlank() }
        assertEquals(EXPECTED, lines.size, "the manifest no longer lists all $EXPECTED recordings")
    }

    private companion object {
        /** What `generate-recordings.ts` produced, before it was deleted with `legacy-web/`. */
        const val EXPECTED = 50
    }
}
