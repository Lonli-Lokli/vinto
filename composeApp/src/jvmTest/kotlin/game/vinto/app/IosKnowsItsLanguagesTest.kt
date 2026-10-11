package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * iOS is told every language the app speaks.
 *
 * Reported 2026-10-09: *"Vinto has translations but they are invisible on iOS."* All twenty were
 * in the bundle, and iOS could not see one of them. Compose keeps its strings in
 * `compose-resources/`, not in `.lproj` folders, so the bundle reads as English-only unless
 * `Info.plist` lists the languages itself. Without that list the App Store shows English and
 * nothing else, iOS Settings has no Language row for the app, and the system hands the app English
 * whatever the phone is set to. game-deduction found the same thing first (5cc9260).
 *
 * The list is [Language], the same one the in-app picker offers. Chinese is `zh` to Compose,
 * which matches on the language alone, and `zh-Hans` to Apple, which wants the script.
 */
class IosKnowsItsLanguagesTest {

    @Test
    fun iosIsToldEveryLanguageTheAppSpeaks() {
        val plist = File("../iosApp/iosApp/Info.plist")
            .also { assertTrue(it.exists(), "the Info.plist moved") }
            .readText()

        val key = plist.indexOf("<key>CFBundleLocalizations</key>")
        assertTrue(key >= 0, "Info.plist never tells iOS which languages the app speaks")
        val array = plist.substring(plist.indexOf("<array>", key), plist.indexOf("</array>", key))
        val declared = Regex("<string>([^<]+)</string>").findAll(array).map { it.groupValues[1] }.toSet()

        val spoken = Language.entries.map { if (it == Language.CHINESE) "zh-Hans" else it.tag }.toSet()
        assertEquals(spoken, declared, "the languages iOS is told are not the languages the app speaks")
    }
}
