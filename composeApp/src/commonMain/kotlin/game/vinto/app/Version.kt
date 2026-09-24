package game.vinto.app

/**
 * What version this is, for the corner of the home screen, the foot of the settings and the
 * release a crash report is filed under.
 *
 * **The MARKETING version, and each store's own.** The stores do not move in step — iOS can be on
 * 1.1 while Android is still 1.0 (VERSIONING.md) — so there is no one number to write here. It
 * used to be a single `VERSION` held equal to both stores by a test, which is the lockstep
 * VERSIONING.md rules out, written down as a check. Now each build reads the number its store was
 * given: iOS from its bundle (Info.plist takes it from `project.yml`), Android from its package
 * (`versionName` in `androidApp`). Nothing is copied, so nothing can drift.
 *
 * The build number is a separate, machine-monotonic thing — `BUILD_NUMBER` — and never appears
 * here.
 */
expect fun appVersion(): String

/**
 * The web and desktop builds' version: they have no store, ship from master, and so carry the
 * newest version either store has. `VersionTest` holds it to that.
 */
internal const val WEB_VERSION = "1.1"
