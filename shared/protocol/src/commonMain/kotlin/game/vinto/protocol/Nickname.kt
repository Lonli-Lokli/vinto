package game.vinto.protocol

/**
 * What a player is called online, drawn from a fixed vocabulary rather than typed.
 *
 * It lives here for the same reason [CODE_ALPHABET] does: the **client** mints a name and the
 * **room** has to agree that it is one. Two implementations that resemble each other is the
 * failure `shared/protocol` exists to prevent, and here the cost of disagreeing is not a support
 * question — it is a name the room rejects and a player who cannot sit down.
 *
 * ## Why there is no text field any more
 *
 * There used to be one. `OnlineScreen` asked the player to type a name, `cleanNickname` filtered
 * the character set and the length, and a public room posted the result to `/rooms` where
 * strangers read it.
 *
 * That is **user-generated content** in the sense App Store Review Guideline 1.2 means it: text
 * one person authors and other people see. An app that has it must ship a filter, a report route
 * and a way to block, and must say so in the age-rating questionnaire — which takes the rating
 * off 4+ and makes moderation a standing obligation somebody has to actually staff. For a card
 * game whose entire social surface is "a name above five face-down cards", that is a large tail
 * to keep wagging a small dog.
 *
 * Generating the name removes the category rather than policing it. Nobody authors anything, so
 * there is nothing to filter, nothing to report, and `userGeneratedContent` is honestly false
 * (`vydanne.config.mjs`). The player still gets an identity they chose — they can keep pressing
 * for another one — they just choose from a list instead of a keyboard.
 *
 * ## Why the room checks too
 *
 * [looksMinted] is not defensive programming about our own UI. The client is the *app*, and the
 * room's door is open to anything that speaks the protocol — so a modified client could send any
 * string at all, and "the app has no text field" would stop being the whole truth about what
 * reaches other players' screens. Checking at the room is what makes the claim true of the
 * service and not merely of the build.
 *
 * ## The vocabulary
 *
 * Adjective plus noun, both deliberately dull: weather, landscape, quiet animals, materials.
 * Every pairing has to be safe, because 1024 of them exist and nobody will read them all — so
 * the lists carry no body parts, no nationalities, no religion, nothing scatological, and no
 * word that turns rude next to another one here. Words are ASCII and short enough that
 * `<adjective> <noun>` always fits the 16 characters the room allows.
 *
 * They are **not translated**, and that is deliberate: the name travels over the wire and is
 * shown to every seat at once, so it has to be one string rather than one per reader. A Russian
 * player sees "Quiet Heron" the way they see a username anywhere else.
 */

/** Half the vocabulary. Kept to one short word so the pair always fits the room's 16 characters. */
public val NICKNAME_ADJECTIVES: List<String> = listOf(
    "Amber", "Brave", "Calm", "Clever", "Copper", "Dusty", "Eager", "Early",
    "Frosty", "Gentle", "Golden", "Happy", "Hidden", "Idle", "Jolly", "Keen",
    "Lucky", "Merry", "Misty", "Nimble", "Patient", "Polite", "Quick", "Quiet",
    "Rapid", "Silver", "Sleepy", "Steady", "Sunny", "Tidy", "Velvet", "Wise",
)

/** The other half: animals, weather and landscape. Nothing that names a person or a place. */
public val NICKNAME_NOUNS: List<String> = listOf(
    "Otter", "Heron", "Badger", "Falcon", "Marten", "Beaver", "Ermine", "Osprey",
    "Willow", "Cedar", "Aspen", "Birch", "Maple", "Alder", "Hazel", "Rowan",
    "Comet", "Meadow", "Harbour", "Lantern", "Pebble", "Ribbon", "Anchor", "Compass",
    "Sparrow", "Swallow", "Puffin", "Curlew", "Marmot", "Bison", "Lynx", "Elk",
)

/** How many distinct names exist. 1024 — enough that two strangers rarely collide. */
public val NICKNAME_COUNT: Int = NICKNAME_ADJECTIVES.size * NICKNAME_NOUNS.size

/**
 * The name for [seed], which is a pure function of it.
 *
 * Pure and total on purpose, exactly like everything else this repository generates from a seed:
 * the same device gets the same name back after a reinstall if it still holds its guest id, a
 * test can name a seat without a clock, and "press for another" is just another seed rather than
 * a second code path.
 *
 * The two halves are drawn with different divisors so that stepping the seed by one changes the
 * noun and stepping it by 32 changes the adjective — otherwise pressing the button repeatedly
 * walks the noun list alphabetically, which looks broken rather than random.
 */
public fun mintNickname(seed: Long): String {
    // `rem` on a negative Long is negative in Kotlin, and a negative index throws. Masking off
    // the sign bit is cheaper than a branch and keeps this total for every Long, including
    // Long.MIN_VALUE, which `abs` does not.
    val positive = seed and Long.MAX_VALUE
    val adjective = NICKNAME_ADJECTIVES[(positive / NICKNAME_NOUNS.size % NICKNAME_ADJECTIVES.size).toInt()]
    val noun = NICKNAME_NOUNS[(positive % NICKNAME_NOUNS.size).toInt()]
    return "$adjective $noun"
}

/**
 * Whether [name] is one this vocabulary can produce.
 *
 * Exact, and case-sensitive. A looser check — "both words are in the lists somewhere" — would
 * accept "otter Quiet", which is not a name anything here mints and is the shape a probe takes
 * when somebody is testing what the door accepts.
 */
public fun looksMinted(name: String): Boolean {
    val parts = name.split(' ')
    if (parts.size != 2) return false
    return parts[0] in NICKNAME_ADJECTIVES && parts[1] in NICKNAME_NOUNS
}
