package game.vinto.app

import game.vinto.app.link.INVITE_HOST

/**
 * The pages that belong to the game but are not in it.
 *
 * On `kupalinka.app` rather than `vinto.kupalinka.app`, and that is deliberate: the privacy
 * policy and the terms cover every game the studio ships, and a per-game copy of them is two
 * documents to keep true instead of one. The game's own host serves the game.
 *
 * Declared here rather than typed into `SettingsScreen` so that `LinkTargetTest` can assert
 * the shape of them without rendering anything, and so a change of address is one file.
 */
object Pages {
    private const val STUDIO = "https://kupalinka.app"

    /** What is counted, what is not, and the switch that turns it off. */
    const val PRIVACY: String = "$STUDIO/privacy"

    /** What a player can expect, and what is expected of them. */
    const val TERMS: String = "$STUDIO/terms"

    /** A bug, an idea, or a round that went wrong. */
    const val CONTACT: String = "$STUDIO/contact"

    /** Where somebody sent the game ends up: the game itself, not the studio. */
    const val GAME: String = "https://$INVITE_HOST"

    /**
     * The card game itself, and the people who made it.
     *
     * **The one address here that is deliberately not ours.** Vinto is somebody else's game;
     * this repository is an unofficial client for it, and every screen that says so links
     * here. `SettingsLinksTest` knows about the exception and asserts it by name, so nothing
     * else can quietly slip an outside host into this file.
     */
    const val OFFICIAL: String = "https://vinto.game"

    /** This app's own page on the studio's site: what it is, and what else is on the shelf. */
    const val THIS_APP: String = "$STUDIO/games/vinto"
}
