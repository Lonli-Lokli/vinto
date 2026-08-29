package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.VintoJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * How fast the table plays what happened.
 *
 * A setting rather than a constant because the right answer is not the same for everybody or
 * even for one person twice: learning the game, every card wants following; the twentieth
 * round, the same animation is a wait. The scale multiplies every duration in the animation
 * layer at once — one number, so a card, a peek and the pause between turns keep their
 * proportions to each other whichever end of the dial it is on.
 */
@Serializable
enum class Pace(val serialName: String, val scale: Float) {
    /** For learning it, and for watching what the bots are up to. */
    @SerialName("calm")
    CALM("calm", 1.5f),

    @SerialName("steady")
    STEADY("steady", 1f),

    /** For somebody who knows the game and wants their turn back. */
    @SerialName("brisk")
    BRISK("brisk", 0.6f),
}

/** Which palette, regardless of what the phone is set to. */
@Serializable
enum class ThemeChoice(val serialName: String) {
    @SerialName("system")
    SYSTEM("system"),

    @SerialName("light")
    LIGHT("light"),

    @SerialName("dark")
    DARK("dark"),
}

/**
 * Whether the table moves, as opposed to how fast ([Pace]).
 *
 * Reduced motion is not a faster pace — it is *no movement, same information*: cards appear
 * where they land instead of flying, but every dwell, ring, verdict and line stays, because
 * those are the game being narrated rather than decorated. Vestibular sensitivity is the
 * usual reason to want this, and it is exactly the case a speed dial cannot serve: faster
 * movement is worse, not better.
 */
@Serializable
enum class MotionChoice(val serialName: String) {
    /** Follow the platform's accessibility preference, where the platform exposes one. */
    @SerialName("system")
    SYSTEM("system"),

    @SerialName("full")
    FULL("full"),

    @SerialName("reduced")
    REDUCED("reduced"),
    ;

    /** The one decision this setting exists to make, given what the platform says. */
    fun reduced(systemSaysReduce: Boolean): Boolean = when (this) {
        SYSTEM -> systemSaysReduce
        FULL -> false
        REDUCED -> true
    }
}

/**
 * Everything the player has chosen, as opposed to everything they have played.
 *
 * Kept apart from [SavedGame] on purpose: a preference outlives the round it was set in, and
 * abandoning a game must not reset the pace back to something the player has already decided
 * they dislike.
 */
@Serializable
data class Settings(
    @SerialName("version") val version: Int = FORMAT,
    val difficulty: Difficulty = Difficulty.MODERATE,
    val pace: Pace = Pace.STEADY,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** A small kick under the thumb when a card lands or a rule bites. */
    val haptics: Boolean = true,
    /** See [MotionChoice]. A default-added field: an older settings file still decodes. */
    val motion: MotionChoice = MotionChoice.SYSTEM,
    /** The table's four sounds: a card dealt, a card landing, a penalty, the round ending. */
    val sound: Boolean = true,
    /**
     * Whether anonymous counts may be sent — rounds played, how far people get, what a room
     * costs. Never anything identifying: see `AnalyticsEvent`, where a room code or a
     * nickname is unrepresentable rather than filtered.
     *
     * Defaults to on, and the platform can still override it: a Global Privacy Control or
     * Do-Not-Track signal wins over this being true, because a browser sending one is a
     * person who already answered the question. A default-added field, so an older settings
     * file still decodes.
     */
    val analytics: Boolean = true,
) {
    companion object {
        /** Bumped when the shape changes; an older file is replaced by the defaults. */
        const val FORMAT = 1
    }
}

private const val KEY = "vinto.settings"

/**
 * The player's settings, or the defaults.
 *
 * Never throws, for the same reason [loadGame] does not: a preferences file this app wrote
 * badly must not be a reason it cannot start.
 */
fun Vault.loadSettings(): Settings {
    val stored = read(KEY) ?: return Settings()

    return try {
        VintoJson.decodeFromString(Settings.serializer(), stored)
            .takeIf { it.version == Settings.FORMAT } ?: Settings()
    } catch (_: SerializationException) {
        Settings()
    } catch (_: IllegalArgumentException) {
        Settings()
    }
}

/** Writes the settings. One small string, written when something is changed. */
fun Vault.saveSettings(settings: Settings) {
    write(KEY, VintoJson.encodeToString(Settings.serializer(), settings))
}
