package game.vinto.app

import game.vinto.client.Vault

/**
 * None, and that is the answer rather than a gap.
 *
 * Android's ordinary vault already outlives an uninstall: `AndroidStorage` writes the
 * `SharedPreferences` file `vinto.xml`, `AndroidManifest.xml` sets `allowBackup`, and both
 * `res/xml/backup_rules.xml` (Android 12 and up) and `res/xml/backup_rules_legacy.xml`
 * (Nougat to Android 11) name that exact file for cloud backup *and* device-to-device transfer.
 * So the count rides to the next install on the backup that was already carrying the settings
 * and the saved game.
 *
 * A second store here would be a second copy of a solved problem, with a merge that never had
 * anything to reconcile. `Enduring.kt` says what null means.
 *
 * The one case it does not cover is a phone with backup switched off, which is the owner's
 * decision about their own device and not something an app should route around.
 */
actual fun enduringVault(): Vault? = null
