package game.vinto.app

/** See [Storage.wasmJs.kt]'s note on why this is not `kotlin.js.Date`. */
private fun nowMillis(): Double = js("Date.now()")

/** See the expect in `Counting.kt`. */
actual fun elapsedMs(): Long = nowMillis().toLong()
