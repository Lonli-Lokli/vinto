// A COPY of games-core's `games.core.release.AndroidPlatformAudit`
// (gulnya: src/desktopMain/kotlin/games/core/release/AndroidPlatformAudit.kt, as of b2fb1f6 plus the
// edge-to-edge check added after it the same day, 2026-10-05).
// Vinto does not depend on games-core and must build without ../gulnya checked out, so the audit every
// other game imports is carried here by hand. KEEP THE TWO IN STEP: change games-core's first, then bring
// the change here, so Vinto's release is held to the same rules as the other four. The checks and their
// messages are the same; what differs is the package, this header, and the layout Vinto's detekt asks
// for (110 columns, constants first, wrapped call chains). One difference in shape: games-core moved the
// edge-to-edge check into an object of its own, `AndroidEdgeToEdgeAudit` (AndroidEdgeToEdgeAudit.kt),
// because its detekt allows 11 functions per object. Vinto's allows 29, so here it stays in this object
// as `edgeToEdgeComplaints` and `deprecatedWindowApiComplaints`; bring changes to that file here.
package game.vinto.release

import java.io.File

/**
 * **What old and new Android do to a Compose game, asserted rather than assumed** (release audit,
 * 2026-10-05).
 *
 * Part of [AndroidConfigAudit.complaints], so every game's `AndroidReleaseConfigTest` inherits these too:
 * force-dark off, a per-app language list that matches the shipped languages, an activity that rides out
 * a fold, a resize or a display-size change, and edge to edge without what Android 15 deprecated. Like the
 * rest of the audit it reads files, not behaviour.
 */
object AndroidPlatformAudit {

    private val ENABLE_EDGE_TO_EDGE = Regex("""\benableEdgeToEdge\s*\(|\bEdgeToEdge\.enable\s*\(""")

    /** Android 15's edge-to-edge deprecations as Kotlin or Java writes them, getters and setters both. */
    private val DEPRECATED_CODE = listOf(
        Regex("""\bsetStatusBarColor\s*\(|\.statusBarColor\b"""),
        Regex("""\bsetNavigationBarColor\s*\(|\.navigationBarColor\b"""),
        Regex("""\bsetNavigationBarDividerColor\s*\(|\.navigationBarDividerColor\b"""),
        Regex("""\bsetStatusBarContrastEnforced\s*\(|\.isStatusBarContrastEnforced\b"""),
        Regex("""\bsetDecorFitsSystemWindows\s*\("""),
        Regex("""\bLAYOUT_IN_DISPLAY_CUTOUT_MODE_(SHORT_EDGES|DEFAULT|NEVER)\b"""),
    )

    /** The same deprecations as theme items. A cutout mode passes only as `always`. */
    private val DEPRECATED_THEME = Regex(
        """name="(android:(?:statusBarColor|navigationBarColor|navigationBarDividerColor|""" +
            """enforceStatusBarContrast|windowTranslucentStatus|windowOptOutEdgeToEdgeEnforcement|""" +
            """windowLayoutInDisplayCutoutMode))"[^>]*>(?!\s*always\s*<)""",
    )

    private val FORCE_DARK_OFF = Regex("""name="android:forceDarkAllowed"[^>]*>\s*false\s*<""")

    /**
     * Android 7 to 13 report these languages by their old codes; the copies under them are not extra
     * languages.
     */
    private val LEGACY_CODES = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

    /** What the launcher activity must handle itself rather than be recreated for. */
    private val CONFIG_CHANGES = listOf(
        "orientation",
        "screenSize",
        "screenLayout",
        "smallestScreenSize",
        "density",
        "keyboardHidden",
        "uiMode",
    )

    /** Everything wrong with [module]'s platform setup, empty when it is right. */
    fun complaints(module: File, manifestDir: String = "src/androidMain"): List<String> =
        forceDarkComplaints(module, manifestDir) +
            localeConfigComplaints(module, manifestDir) +
            configChangesComplaints(module, manifestDir) +
            edgeToEdgeComplaints(module, manifestDir)

    /**
     * The app theme sets `android:forceDarkAllowed` to false. Android 10+ may darken a light app on its
     * own, and some OEM skins (MIUI) do it whatever the app says unless this is off; every game here draws
     * its own light and dark schemes, measured for contrast, and an automatic inversion throws that
     * measurement away. All five titles lacked it.
     */
    fun forceDarkComplaints(module: File, manifestDir: String = "src/androidMain"): List<String> {
        val res = File(module, "$manifestDir/res")
        val declared = valuesXml(res).any { file ->
            FORCE_DARK_OFF.containsMatchIn(stripXmlComments(file.readText()))
        }
        return if (declared) {
            emptyList()
        } else {
            listOf(
                "no theme under ${res.path} sets <item name=\"android:forceDarkAllowed\">false</item> — " +
                    "Android 10+ and OEM skins may invert the game's own measured palette",
            )
        }
    }

    /**
     * The manifest names `@xml/locales_config`, and that file lists exactly the languages the game ships
     * in `composeResources` (English for `values`, the language of each `values-xx`). Android 13+ builds
     * its per-app language list from this file; compose-resources are invisible to AGP's own generator,
     * so without it the game is missing from Settings > Apps > Language, and a file that drifts from the
     * shipped set offers a language that is not there or hides one that is.
     */
    fun localeConfigComplaints(module: File, manifestDir: String = "src/androidMain"): List<String> {
        val manifest = File(module, "$manifestDir/AndroidManifest.xml")
        if (!manifest.isFile) return listOf("${manifest.path} is missing")
        val out = mutableListOf<String>()
        if (!stripXmlComments(manifest.readText())
                .contains("""android:localeConfig="@xml/locales_config"""")
        ) {
            out += "the manifest does not declare android:localeConfig=\"@xml/locales_config\" — " +
                "Android 13+ cannot list the game under per-app languages"
        }
        val config = File(module, "$manifestDir/res/xml/locales_config.xml")
        if (!config.isFile) return out + "${config.path} is missing"

        val listed = Regex("""<locale\s+android:name="([^"]+)"""")
            .findAll(stripXmlComments(config.readText()))
            .map { it.groupValues[1].substringBefore('-').lowercase() }
            .toSet()
        val shipped = shippedLanguages(module)
        if (shipped.isEmpty()) return out
        (shipped - listed).sorted()
            .forEach { out += "locales_config.xml does not list \"$it\", which the game ships" }
        (listed - shipped).sorted()
            .forEach { out += "locales_config.xml lists \"$it\", which the game does not ship" }
        return out
    }

    /**
     * The launcher activity's `android:configChanges` covers rotation, resizing and folding. Each game
     * keeps its screen in Compose state; without these, a fold, a split-screen resize or a display-size
     * change recreates the activity and drops an open board, sheet or mode (Niva's random field, Vodar's
     * picks, Palon's duel). Compose handles the new configuration itself.
     */
    fun configChangesComplaints(module: File, manifestDir: String = "src/androidMain"): List<String> {
        val manifest = File(module, "$manifestDir/AndroidManifest.xml")
        if (!manifest.isFile) return listOf("${manifest.path} is missing")
        val declared = Regex("""android:configChanges="([^"]*)"""")
            .findAll(stripXmlComments(manifest.readText()))
            .flatMap { it.groupValues[1].split('|') }
            .map { it.trim() }
            .toSet()
        val missing = CONFIG_CHANGES.filterNot { it in declared }
        return if (missing.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "the activity's android:configChanges lacks ${missing.joinToString("|")} — a fold, " +
                    "resize or display-size change recreates it and loses the open screen",
            )
        }
    }

    /**
     * Edge to edge, the way Play checks it (Palon's Play Console, 2026-10-05: "Edge-to-edge may not display
     * for all users" and "Your app uses deprecated APIs or parameters for edge-to-edge"). The app's Android
     * code calls androidx `enableEdgeToEdge()`, which makes Android 14 and older draw behind the bars as 15+
     * does by force, and is the call Play asks for. And nothing of OURS uses what Android 15 deprecated
     * ([deprecatedWindowApiComplaints]).
     *
     * What stays in Play's second warning after this is androidx.activity's own, and is not a complaint: its
     * `EdgeToEdgeApi28` sets SHORT_EDGES on Android 9 and 10, where ALWAYS does not exist yet
     * (`EdgeToEdgeApi30` sets ALWAYS). Read from the bytecode of 1.13.0 and of 1.14.0-alpha03, so no upgrade
     * removes it. In Palon's builds 98, 99, 124 and 130 it is the only write of the cutout mode in the whole
     * app; R8 outlines it into a one-line helper (`x0.o` in 98 and 99), which is the shape of the `w0.o` Play
     * names. Removing it would mean dropping the very call the first warning asks for.
     */
    fun edgeToEdgeComplaints(module: File, manifestDir: String = "src/androidMain"): List<String> {
        val root = File(module, manifestDir)
        val calls = sources(root).any { file ->
            ENABLE_EDGE_TO_EDGE.containsMatchIn(stripCodeComments(file.readText()))
        }
        val enable = if (calls) {
            emptyList()
        } else {
            listOf(
                "nothing under ${root.path} calls enableEdgeToEdge() — Android 14 and older keep opaque " +
                    "bars, and Play warns that edge-to-edge may not display for all users",
            )
        }
        return enable + deprecatedWindowApiComplaints(root)
    }

    /**
     * Every use under [root] of a window API or theme attribute that Android 15 deprecated for edge to edge:
     * the bar colours, the status-bar contrast, `setDecorFitsSystemWindows`, the translucent-status and
     * opt-out attributes, and every cutout mode but ALWAYS. For an app targeting 35+ they do nothing (15+
     * draws behind transparent bars and treats every cutout mode as ALWAYS), each one is a line in Play's
     * warning, and `enableEdgeToEdge()` does the same job on older Android. Also run over games-core's own
     * Android sources, which ship in every game (in Vinto: composeApp's `src/androidMain`).
     */
    fun deprecatedWindowApiComplaints(root: File): List<String> {
        val out = mutableListOf<String>()
        for (file in sources(root)) {
            val code = stripCodeComments(file.readText())
            for (regex in DEPRECATED_CODE) {
                val use = regex.find(code) ?: continue
                out += "${file.path} uses ${use.value.trim()} — deprecated in Android 15 for edge to " +
                    "edge and named in Play's warning; enableEdgeToEdge() already does this on older Android"
            }
        }
        for (file in valuesXml(File(root, "res"))) {
            DEPRECATED_THEME.findAll(stripXmlComments(file.readText())).forEach { item ->
                out += "${file.path} sets ${item.groupValues[1]} — deprecated in Android 15 for edge to " +
                    "edge and named in Play's warning; the window background already paints the first frame"
            }
        }
        return out
    }

    private fun sources(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()

    private fun stripCodeComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")

    private fun valuesXml(res: File): List<File> =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }

    /**
     * English for `values`, then the language of each `values-xx[-rYY]` under commonMain's
     * composeResources.
     */
    private fun shippedLanguages(module: File): Set<String> {
        val res = File(module, "src/commonMain/composeResources")
        if (!res.isDirectory) return emptySet()
        return res.listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }
            .orEmpty()
            .filter { File(it, "strings.xml").isFile }
            .map {
                if (it.name == "values") {
                    "en"
                } else {
                    it.name.removePrefix("values-").substringBefore('-').lowercase()
                }
            }
            // values-iw / values-in are generated copies of he / id for Android 7 to 13
            // (tools/legacy-locales.mjs).
            .map { LEGACY_CODES[it] ?: it }
            .toSet()
    }

    private fun stripXmlComments(text: String): String = text.replace(Regex("""<!--[\s\S]*?-->"""), "")
}
