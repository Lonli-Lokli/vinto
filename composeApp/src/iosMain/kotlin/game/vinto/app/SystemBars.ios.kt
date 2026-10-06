package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIContentSizeCategory
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategorySmall
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleDefault
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/**
 * Hands the theme being drawn to the [GameUIViewController] above this composition, which turns the
 * status bar's icons to match. A copy of games-core's, which Vinto does not use.
 *
 * Measured on the simulator (2026-10-05, iOS 26.5 and iOS 27.0): iOS 26 and later sample what is under
 * the status bar and pick legible icons by themselves while the style is the default, so a Light table
 * on a phone in Dark already showed dark icons there. Before iOS 26 the default style follows the
 * interface style, the phone's own setting (Apple's `UIStatusBarStyleDefault`): white icons on paper.
 * No runtime older than 26 can be simulated here, so that half is documented rather than seen. An
 * explicit style is honoured on both runtimes and beats the sampling.
 *
 * It changes no trait, unlike the window's `overrideUserInterfaceStyle`, which would turn the icons too
 * but also the trait `isSystemInDarkTheme()` reads: a player on the System theme would get their own
 * override back and stop following the phone. Here they keep following it, live.
 */
@Composable
actual fun SystemBars(dark: Boolean) {
    val controller = LocalUIViewController.current
    SideEffect {
        val host = controller.systemBarsHost()
        if (host != null) {
            host.dark = dark
        } else if (!warned) {
            warned = true
            NSLog("SystemBars: no GameUIViewController above this composition; the default style stays")
        }
    }
}

/**
 * Compose's `ComposeUIViewController`, inside a parent that answers which way round the status bar's
 * icons go: `MainViewController()` returns this. Compose's own controller cannot be subclassed, and its
 * delegate's `preferredStatusBarStyle` is deprecated in favour of exactly this parent. The SwiftUI host
 * needs no change: its hosting controller asks the controller a `UIViewControllerRepresentable` returns
 * (`childForStatusBarStyle`).
 *
 * It also sizes the text by Apple's Dynamic Type table rather than Compose's ([DynamicTypeText]), so
 * text reaches the 200% the Larger Text accessibility label asks for.
 */
@Suppress("FunctionNaming") // named like the ComposeUIViewController it stands in for
fun GameUIViewController(content: @Composable () -> Unit): UIViewController =
    SystemBarsHost(ComposeUIViewController { DynamicTypeText(content) })

/**
 * [content] under a `LocalDensity` whose `fontScale` follows Apple's body text ([DynamicType]: up to
 * 2.35 at AccessibilityExtraLarge and above) instead of Compose's own table, which stops at 1.8
 * (owner, 2026-10-05). The platform's `density`, its pixels per point, is kept as it is; only
 * `fontScale` is replaced, so nothing but `sp` changes size. App's tablet scale multiplies into it.
 * A copy of the one in games-core's `GameUIViewController`, which Vinto does not use.
 *
 * Live: a player who changes the text size while Vinto is open sees it at once. Two signals re-read
 * the size, whichever comes first: UIKit's `UIContentSizeCategoryDidChangeNotification`, and
 * Compose's own density, which Compose renews when the trait collection changes.
 *
 * It reads the size and changes nothing: no trait, no window override, so `isSystemInDarkTheme()`
 * and the status bar's [SystemBars] work as before.
 */
@Composable
private fun DynamicTypeText(content: @Composable () -> Unit) {
    var changes by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            UIContentSizeCategoryDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { changes++ }
        onDispose { center.removeObserver(observer) }
    }
    val platform = LocalDensity.current
    val density = remember(platform, changes) {
        Density(platform.density, DynamicType.fontScale(currentDynamicTypeSize()))
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/** The phone's text size now, from the application, which every trait collection's size comes from. */
private fun currentDynamicTypeSize(): DynamicTypeSize? =
    UIApplication.sharedApplication.preferredContentSizeCategory.toDynamicTypeSize()

/** UIKit's name for a size, as [DynamicTypeSize]; null for "unspecified" or a size newer than the table. */
private fun UIContentSizeCategory.toDynamicTypeSize(): DynamicTypeSize? = when (this) {
    UIContentSizeCategoryExtraSmall -> DynamicTypeSize.ExtraSmall
    UIContentSizeCategorySmall -> DynamicTypeSize.Small
    UIContentSizeCategoryMedium -> DynamicTypeSize.Medium
    UIContentSizeCategoryLarge -> DynamicTypeSize.Large
    UIContentSizeCategoryExtraLarge -> DynamicTypeSize.ExtraLarge
    UIContentSizeCategoryExtraExtraLarge -> DynamicTypeSize.ExtraExtraLarge
    UIContentSizeCategoryExtraExtraExtraLarge -> DynamicTypeSize.ExtraExtraExtraLarge
    UIContentSizeCategoryAccessibilityMedium -> DynamicTypeSize.AccessibilityMedium
    UIContentSizeCategoryAccessibilityLarge -> DynamicTypeSize.AccessibilityLarge
    UIContentSizeCategoryAccessibilityExtraLarge -> DynamicTypeSize.AccessibilityExtraLarge
    UIContentSizeCategoryAccessibilityExtraExtraLarge -> DynamicTypeSize.AccessibilityExtraExtraLarge
    UIContentSizeCategoryAccessibilityExtraExtraExtraLarge ->
        DynamicTypeSize.AccessibilityExtraExtraExtraLarge
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
private class SystemBarsHost(
    private val compose: UIViewController,
) : UIViewController(nibName = null, bundle = null) {

    /** The theme being drawn, from [SystemBars]; null until the first composition chose one. */
    var dark: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            setNeedsStatusBarAppearanceUpdate()
        }

    override fun viewDidLoad() {
        super.viewDidLoad()
        // The parent link first, so the first composition (which starts when Compose's view loads, on
        // the next line) can already find this controller above it.
        addChildViewController(compose)
        compose.view.setFrame(view.bounds)
        compose.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(compose.view)
        compose.didMoveToParentViewController(this)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle = when (dark) {
        null -> UIStatusBarStyleDefault
        true -> UIStatusBarStyleLightContent
        false -> UIStatusBarStyleDarkContent
    }

    // Compose's own answer. The other bar questions (`childViewControllerForHomeIndicatorAutoHidden`
    // and friends) are declared in UIKit categories, which Kotlin cannot override, so their defaults
    // are unchanged.
    override fun prefersStatusBarHidden(): Boolean = compose.prefersStatusBarHidden()
}

private fun UIViewController.systemBarsHost(): SystemBarsHost? =
    generateSequence(this) { it.parentViewController }.firstNotNullOfOrNull { it as? SystemBarsHost }

/** Main thread only, like everything in a composition's side effects. */
private var warned = false
