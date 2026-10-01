import ComposeApp
import SwiftUI

@main
struct iOSApp: App {
    init() {
        // First, before anything that can block: compile the Metal programs Compose's first frame needs on a
        // background thread, so a cold shader cache (every install, every iOS update) is not paid frozen on the
        // main thread. Niva (NIVA-6) and Vodar (1.0+108) both stalled 2 s there. FirstFrameWarmUp (composeApp,
        // a copy of games-core's) has the reasoning and its honest limits.
        MainViewControllerKt.WarmUpFirstFrame()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // An invitation, in both the forms iOS can deliver it.
                //
                // `onOpenURL` covers the custom scheme (vinto://7KQ2MP), which works with no
                // hosted file and no verification. `onContinueUserActivity` covers the
                // Universal Link (https://vinto.kupalinka.app/r/7KQ2MP), which is the better
                // one — it opens the app *and* falls back to the website for somebody who
                // does not have it — but only once
                // https://vinto.kupalinka.app/.well-known/apple-app-site-association names
                // this app's team id and bundle id. Until that file is published the https
                // half silently does nothing, which is why the scheme is here as well.
                //
                // Both do the same three lines of work: hand the URL to Kotlin, where what a
                // link *means* is decided and tested (composeApp .../link/Invite.kt). Nothing
                // about room codes is written twice.
                .onOpenURL { url in
                    MainViewControllerKt.HandleOpenedLink(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    MainViewControllerKt.HandleOpenedLink(url: activity.webpageURL?.absoluteString)
                }
        }
    }
}
