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

    /**
     * The studio's other games.
     *
     * The fragment is not decoration: `kupalinka.app` is one page and `#games` is the section
     * of it that lists them, so dropping it lands a curious player at the top of a page about
     * the studio rather than on the shelf they were promised.
     */
    const val GAMES: String = "$STUDIO/#games"

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

    /**
     * The rules, from the people whose game it is.
     *
     * The help sheet says what a card does *in this app*, which is the question somebody has
     * mid-turn. It is deliberately not the whole rulebook — `VINTO_RULES.md` runs to four pages
     * and the sheet is read one-handed with a card waiting. This is where the rest lives, and
     * pointing at the original rather than restating them keeps one copy authoritative: if this
     * app and that page disagree, that page is right and this app is the bug.
     *
     * The second address here that is deliberately not ours, and named in `SettingsLinksTest`
     * for the same reason [OFFICIAL] is.
     */
    const val RULES: String = "$OFFICIAL/rules/"

    /** This app's own page on the studio's site: what it is, and what else is on the shelf. */
    const val THIS_APP: String = "$STUDIO/games/vinto"

    /**
     * Where somebody can say thanks with whatever amount they choose.
     *
     * **The web and the desktop only, and that is not a style note.** Sending a player to an
     * outside payment page is App Store 3.1.1 — collecting money for a developer outside in-app
     * purchase — and Google's policy says the same. Neither reaches a build that is in no store,
     * which is what the web and the desktop are; the phones get a fixed-price in-app purchase
     * instead (`Support.kt`).
     *
     * The third address in this file that is deliberately not ours, named in `SettingsLinksTest`
     * beside the other two. `SupportLinkTest` is the one that keeps it off the phones.
     */
    const val SUPPORT: String = "https://buymeacoffee.com/lonlilokliv"

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

    /** The listings themselves, for "update the app": the review sheet is the wrong door for that. */
    const val APPLE_LISTING: String = "https://apps.apple.com/app/id6803030533"
    const val PLAY_LISTING: String = "https://play.google.com/store/apps/details?id=app.kupalinka.vinto"
    const val PLAY_REVIEW: String = "https://play.google.com/store/apps/details?id=app.kupalinka.vinto"
}
