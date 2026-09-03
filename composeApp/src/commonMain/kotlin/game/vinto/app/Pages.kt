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

    /**
     * The two store listings, for the rate button. `storeReviewUrl()` picks between them.
     *
     * **Both are filled in before either listing is live, deliberately.** A dead link is a
     * temporary embarrassment; shipping a build to add two constants costs a whole review cycle,
     * and until release the only people who can press this are testers on TestFlight and the
     * Play internal track. Each heals itself the moment its store approves.
     *
     * The Apple id is the created app record's (`vydanne inspect` — 6803030533), not a guess.
     * The Play package has no record yet at all: the Publishing API cannot create one, so
     * somebody has to make the app in the Play Console by hand. The URL below is what that
     * listing will answer on, because a Play listing's address is its package name.
     *
     * `?action=write-review` opens Apple's review sheet directly rather than the listing, which
     * is the difference between "rate" and "look at the app you already have". Play has no
     * equivalent parameter that works from outside the store app, so it gets the listing.
     */
    const val APPLE_REVIEW: String = "https://apps.apple.com/app/id6803030533?action=write-review"
    const val PLAY_REVIEW: String = "https://play.google.com/store/apps/details?id=app.kupalinka.vinto"
}
