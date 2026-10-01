package game.vinto.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Point3
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.ShadowUtils
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skia.impl.Managed
import platform.Foundation.NSLog
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLResourceStorageModePrivate
import platform.Metal.MTLResourceStorageModeShared
import platform.Metal.MTLStorageModePrivate
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureUsageRenderTarget
import platform.Metal.MTLTextureUsageShaderRead
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import kotlin.concurrent.AtomicInt
import kotlin.time.TimeSource

/**
 * Compile the Metal programs Compose's first frame needs on a background thread, before that frame asks for
 * them. Call [start] as the FIRST line of the iOS entry point (`iOSApp.init`), ahead of everything else.
 *
 * **The stall.** Compose on iOS renders on the main thread. The first time Skia draws a given kind of shape
 * it must have Metal compile a program for it, behind a synchronous wait, so the whole UI is frozen until the
 * compiler answers. The compiled programs are cached on disk, but the cache is cold after an install and
 * after every iOS update, so every player pays it once per install or OS update — and pays it on the first
 * frame, because that frame draws every kind of shape the app uses at once. Two reports so far, two different
 * stages of the same thing:
 *  - NIVA-6 (Niva 1.2, iPhone SE 2nd gen, iOS 26.6): the BLIT program, through `FillRRectOp`'s static vertex
 *    buffer upload → `findOrCreateBlitProgramVariant` → `MTLCompiler`.
 *  - Vodar 1.0+108 (iPhone 15 Pro Max, iOS 27.0.1, 2 s after launch): a RENDER pipeline, through
 *    `CircleOp::onExecute` → `GrMtlPipelineStateBuilder::finalize`. A fast phone; the cache had just been
 *    emptied by the OS update.
 *
 * **The warm-up.** Make the same requests first, off the main thread: one raw buffer-to-buffer blit (Niva's
 * original mitigation, kept because it is cheap and its target is known), then a private Skia context that
 * draws the primitives Compose emits — rects, rounded rects, circles, ovals, paths, strokes, text,
 * gradients, images, clipped draws, layers, a blur and a shadow — into an off-screen surface built the way
 * Compose builds its window surface (a BGRA8 Metal texture, top-left origin, sRGB). Metal's compiler caches
 * by program, not by Skia context, so when Compose's own context asks for the same program it is answered
 * from the cache. This is the idea behind Flutter's `DefaultShaderWarmUp`, which drew the same kind of
 * picture for the same reason.
 *
 * **Honest limits.** A mitigation, not a fix: the compile still happens, it just happens off the critical
 * path. A program the first frame needs that is not drawn here, or one the main thread asks for while it is
 * still compiling here, is paid on the main thread as before — never more than before. No test can show it
 * works: the Simulator compiles with the host Mac's driver against a warm cache. It is verified on a real
 * phone with a cold cache (delete the app and reinstall), and by the hang reports stopping.
 *
 * **A COPY of games-core's `games.core.system.FirstFrameWarmUp`** (gulnya, 2026-10-01). Vinto does not depend
 * on games-core, so the portfolio's one warm-up is carried here by hand: change it THERE first, then bring
 * the change here, so the two never drift into warming different programs.
 *
 * Fire-and-forget and failure-proof: every step is checked, any refusal ends the warm-up, and the app is then
 * exactly where it would have been without it.
 */
internal object FirstFrameWarmUp {
    private const val SIZE = 256
    private const val BLIT_BYTES = 4_096
    private const val BACKGROUND = 0xFFF4EFE6.toInt()
    private const val OPAQUE = 0xFF33415C.toInt()
    private const val TRANSLUCENT = 0x8033415C.toInt()
    private const val SHADOW_AMBIENT = 0x0A000000
    private const val SHADOW_SPOT = 0x40000000
    private val claimed = AtomicInt(0)

    /** Start the warm-up on a background queue. Returns at once; a second call does nothing. */
    fun start() {
        if (!claimed.compareAndSet(0, 1)) return
        // USER_INITIATED, not UTILITY: this races the first frame, and a starved queue warms nothing in time.
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
            val began = TimeSource.Monotonic.markNow()
            val warmed = warmUp()
            // One line per launch, for timing a cold launch on a real phone in Console.app: a cold cache
            // shows as hundreds of milliseconds here, a warm one as a few dozen.
            //
            // The message is built in Kotlin and passed as the format, with NO arguments, and it must never
            // contain a `%`: a Kotlin String handed to NSLog's varargs for `%@` is not bridged to an
            // NSString, and NSLog dereferences it as one — SIGSEGV on this thread, found launching Vodar on
            // the Simulator (2026-10-01).
            val outcome = if (warmed) "warmed" else "declined"
            NSLog("Vinto FirstFrameWarmUp: $outcome in ${began.elapsedNow().inWholeMilliseconds} ms")
        }
    }

    /**
     * The whole warm-up, synchronously, on the caller's thread: true when every step ran. Internal for the
     * test.
     */
    @OptIn(ExperimentalForeignApi::class)
    internal fun warmUp(): Boolean = runCatching {
        val device = MTLCreateSystemDefaultDevice() ?: return@runCatching false
        warmBlit(device) && warmSkia(device)
    }.getOrDefault(false)

    /**
     * Shared → private is the direction Skia's static-buffer upload takes, and part of what picks the
     * variant.
     */
    private fun warmBlit(device: MTLDeviceProtocol): Boolean {
        val bytes = BLIT_BYTES.toULong()
        val source = device.newBufferWithLength(bytes, MTLResourceStorageModeShared) ?: return false
        val destination = device.newBufferWithLength(bytes, MTLResourceStorageModePrivate) ?: return false
        val commands = device.newCommandQueue()?.commandBuffer()
        val blit = commands?.blitCommandEncoder()
        if (commands == null || blit == null) return false
        blit.copyFromBuffer(source, 0u, destination, 0u, bytes)
        blit.endEncoding()
        commands.commit()
        // Waiting is the point: the compile happens during this call, on THIS thread.
        commands.waitUntilCompleted()
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun warmSkia(device: MTLDeviceProtocol): Boolean {
        val queue = device.newCommandQueue() ?: return false
        val descriptor = MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
            MTLPixelFormatBGRA8Unorm,
            SIZE.toULong(),
            SIZE.toULong(),
            false,
        )
        descriptor.usage = MTLTextureUsageRenderTarget or MTLTextureUsageShaderRead
        descriptor.storageMode = MTLStorageModePrivate
        val texture = device.newTextureWithDescriptor(descriptor) ?: return false

        val context = DirectContext.makeMetal(device.objcPtr(), queue.objcPtr())
        val target = BackendRenderTarget.makeMetal(SIZE, SIZE, texture.objcPtr())
        val surface = Surface.makeFromBackendRenderTarget(
            context,
            target,
            SurfaceOrigin.TOP_LEFT,
            SurfaceColorFormat.BGRA_8888,
            ColorSpace.sRGB,
            SurfaceProps(pixelGeometry = PixelGeometry.UNKNOWN),
        )
        try {
            if (surface == null) return false
            drawEveryPrimitive(surface.canvas)
            // syncCpu: block THIS thread until the GPU work, and so every compile, is done.
            context.flushAndSubmit(surface, syncCpu = true)
            return true
        } finally {
            surface?.close()
            target.close()
            context.close()
        }
    }

    /**
     * What a Compose frame is made of, in roughly the order a first frame meets it: the background, cards and
     * pills, dots and rings, icons, dividers, text, then the decorated variants. Each paint is anti-aliased,
     * as Compose's are, and solid fills come both opaque and translucent because Skia picks a different blend
     * for each.
     */
    private fun drawEveryPrimitive(canvas: Canvas) {
        val opaque = fill(OPAQUE)
        val translucent = fill(TRANSLUCENT)
        val stroke = stroke(3f)
        val hairline = stroke(1f)

        canvas.clear(BACKGROUND)
        for (paint in listOf(opaque, translucent)) {
            canvas.drawRect(Rect.makeXYWH(4f, 4f, 60f, 40f), paint)
            canvas.drawRRect(RRect.makeXYWH(8f, 50f, 120f, 48f, 12f), paint)
            canvas.drawRRect(RRect.makeXYWH(140f, 50f, 100f, 48f, 24f, 24f, 4f, 4f), paint)
            canvas.drawCircle(40f, 140f, 20f, paint)
            canvas.drawCircle(80f, 140f, 4f, paint)
            canvas.drawOval(Rect.makeXYWH(100f, 120f, 60f, 30f), paint)
        }
        canvas.drawRRect(RRect.makeXYWH(8f, 160f, 120f, 40f, 10f), stroke)
        canvas.drawCircle(180f, 140f, 18f, stroke)
        canvas.drawLine(4f, 210f, 250f, 210f, hairline)
        canvas.drawLine(4f, 220f, 250f, 230f, stroke)

        PathBuilder()
            .moveTo(150f, 160f)
            .cubicTo(170f, 150f, 200f, 190f, 230f, 165f)
            .quadTo(240f, 200f, 200f, 205f)
            .lineTo(150f, 200f)
            .closePath()
            .detach()
            .closing { icon ->
                canvas.drawPath(icon, opaque)
                canvas.drawPath(icon, translucent)
                canvas.drawPath(icon, stroke)
                ShadowUtils.drawShadow(
                    canvas, icon, Point3(0f, 0f, 8f), Point3(0f, -300f, 600f), 800f,
                    SHADOW_AMBIENT, SHADOW_SPOT, transparentOccluder = false, geometricOnly = false,
                )
            }

        drawText(canvas, opaque, translucent)

        val gradient = Gradient(
            Gradient.Colors(arrayOf(Color4f(OPAQUE), Color4f(TRANSLUCENT)), null, FilterTileMode.CLAMP),
        )
        Shader.makeLinearGradient(0f, 0f, 120f, 0f, gradient).closing { linear ->
            canvas.drawRect(Rect.makeXYWH(0f, 236f, 120f, 16f), fill(OPAQUE).apply { shader = linear })
            canvas.drawRRect(
                RRect.makeXYWH(130f, 236f, 120f, 16f, 8f),
                fill(OPAQUE).apply { shader = linear },
            )
        }
        Shader.makeRadialGradient(220f, 30f, 24f, gradient).closing { radial ->
            canvas.drawCircle(220f, 30f, 24f, fill(OPAQUE).apply { shader = radial })
        }

        Bitmap().closing { pixels ->
            pixels.allocN32Pixels(16, 16, opaque = false)
            pixels.erase(TRANSLUCENT)
            Image.makeFromBitmap(pixels).closing { image ->
                canvas.drawImageRect(image, Rect.makeXYWH(70f, 4f, 32f, 32f))
            }
        }

        // A rounded clip is folded into the draws under it, so it makes its own programs.
        canvas.save()
        canvas.clipRRect(RRect.makeXYWH(110f, 4f, 80f, 40f, 12f), ClipMode.INTERSECT, antiAlias = true)
        canvas.drawRect(Rect.makeXYWH(100f, 0f, 100f, 50f), opaque)
        canvas.drawCircle(150f, 24f, 14f, translucent)
        canvas.restore()

        // graphicsLayer(alpha) and Modifier.blur both draw through an off-screen layer.
        canvas.saveLayer(Rect.makeXYWH(0f, 0f, 128f, 128f), Paint().apply { alpha = 128 })
        canvas.drawRRect(RRect.makeXYWH(10f, 10f, 100f, 100f, 16f), opaque)
        canvas.restore()
        ImageFilter.makeBlur(4f, 4f, FilterTileMode.DECAL).closing { blur ->
            canvas.saveLayer(Rect.makeXYWH(128f, 128f, 128f, 128f), Paint().apply { imageFilter = blur })
            canvas.drawCircle(192f, 192f, 30f, opaque)
            canvas.restore()
        }
    }

    /**
     * Glyphs draw from an atlas, a program of their own, whatever the face; only a real face has any glyphs.
     */
    private fun drawText(canvas: Canvas, opaque: Paint, translucent: Paint) {
        val face = FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL) ?: return
        Font(face, 16f).closing { font ->
            canvas.drawString("Warm 0123", 8f, 120f, font, opaque)
            canvas.drawString("Warm 0123", 8f, 136f, font, translucent)
        }
        Font(face, 48f).closing { large -> canvas.drawString("Aa", 160f, 120f, large, opaque) }
    }

    /** Compose anti-aliases every paint, and the AA flag is part of what picks the program. */
    private fun fill(argb: Int) = Paint().apply {
        isAntiAlias = true
        color = argb
    }

    private fun stroke(width: Float) = fill(OPAQUE).apply {
        mode = PaintMode.STROKE
        strokeWidth = width
        strokeCap = PaintStrokeCap.ROUND
    }
}

/** Skia's native objects are not `AutoCloseable` on Native, so `use` is spelled out. */
private inline fun <T : Managed, R> T.closing(block: (T) -> R): R = try {
    block(this)
} finally {
    close()
}
