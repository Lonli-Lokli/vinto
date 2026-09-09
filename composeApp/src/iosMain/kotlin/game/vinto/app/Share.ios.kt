package game.vinto.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * The activity view, which this used to say was worth finishing on a Mac.
 *
 * It returned false and sent every caller to the clipboard, on the reasoning that reaching the
 * presenting view controller meant holding a reference nothing else in this module needed. It does
 * not: `keyWindow.rootViewController` is the one Compose itself is hosted in, and it is how every
 * other game in the portfolio presents this sheet. A clipboard is a poor substitute for a share
 * sheet on the one platform where an invitation is most likely to be sent from a phone.
 */
actual fun shareText(subject: String, body: String): Boolean = present(listOf(body))

/**
 * The code as a picture, with the words under it.
 *
 * The image goes **first** in the list, because `UIActivityViewController` builds its preview from
 * the leading item — a text-first order shows a URL card and buries the thing somebody is meant to
 * scan.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun sharePicture(subject: String, body: String, picture: ByteArray): Boolean {
    if (picture.isEmpty()) return false
    val data = picture.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = picture.size.toULong())
    }
    val image = UIImage.imageWithData(data) ?: return false
    return present(listOf(image, body))
}

/**
 * Present the sheet, and survive an iPad.
 *
 * On a phone the activity view is a modal sheet and the popover controller is null. On an iPad it
 * is a **popover**, and UIKit raises an exception rather than guessing where to point it — so an
 * anchor is not a nicety, it is the difference between a share and a crash on every tablet. The
 * anchor is the middle of the host view, which is where a sheet raised from a settings row wants
 * to appear anyway.
 */
@OptIn(ExperimentalForeignApi::class)
private fun present(items: List<Any>): Boolean {
    val root: UIViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: return false
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = root.view
        root.view.bounds.useContents {
            popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
    }
    root.presentViewController(sheet, animated = true, completion = null)
    return true
}
