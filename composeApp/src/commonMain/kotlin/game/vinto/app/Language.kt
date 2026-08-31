package game.vinto.app

/**
 * The languages the game is offered in.
 *
 * Each carries its own name in its own script, because a language list is the one list a
 * person reads *before* they can read the app: somebody looking for Ukrainian is looking for
 * "Українська", and "Ukrainian" is only useful to somebody who already has the language they
 * were trying to leave.
 *
 * [bundledFace] is not a detail. The app ships Fira Sans, which carries Latin, its extensions
 * and the whole of Cyrillic — so those languages set in the app's own type. The eight that do
 * not are drawn in whatever face the platform substitutes, which is a real visual difference
 * and a deliberate one: no font covering CJK, Arabic, Hebrew, Devanagari and Bengali fits in a
 * phone game's download — Noto Sans SC alone is larger than this app's entire web bundle.
 * `FontCoverageTest` reads this list, so a language added here without its glyphs fails the
 * build rather than shipping tofu.
 *
 * [rightToLeft] is likewise load-bearing rather than descriptive: Compose mirrors a layout
 * from it, and the three that need it are the three that would otherwise read backwards.
 */
enum class Language(
    val tag: String,
    val endonym: String,
    val bundledFace: Boolean,
    val rightToLeft: Boolean = false,
) {
    ENGLISH("en", "English", bundledFace = true),
    INDONESIAN("id", "Bahasa Indonesia", bundledFace = true),
    GERMAN("de", "Deutsch", bundledFace = true),
    SPANISH("es", "Español", bundledFace = true),
    FRENCH("fr", "Français", bundledFace = true),
    ITALIAN("it", "Italiano", bundledFace = true),
    POLISH("pl", "Polski", bundledFace = true),
    PORTUGUESE("pt", "Português", bundledFace = true),
    TURKISH("tr", "Türkçe", bundledFace = true),
    BELARUSIAN("be", "Беларуская", bundledFace = true),
    RUSSIAN("ru", "Русский", bundledFace = true),
    UKRAINIAN("uk", "Українська", bundledFace = true),

    // Below here the app is drawn in the platform's own face. See [bundledFace].
    HEBREW("he", "עברית", bundledFace = false, rightToLeft = true),
    ARABIC("ar", "العربية", bundledFace = false, rightToLeft = true),
    URDU("ur", "اردو", bundledFace = false, rightToLeft = true),
    HINDI("hi", "हिन्दी", bundledFace = false),
    BENGALI("bn", "বাংলা", bundledFace = false),
    KOREAN("ko", "한국어", bundledFace = false),
    JAPANESE("ja", "日本語", bundledFace = false),
    CHINESE("zh", "简体中文", bundledFace = false),
    ;

    companion object {
        /** The tag stored in `Settings.language`, or null for "follow the device". */
        fun withTag(tag: String?): Language? = entries.firstOrNull { it.tag == tag }
    }
}
