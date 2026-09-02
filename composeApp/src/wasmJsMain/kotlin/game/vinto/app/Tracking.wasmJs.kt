package game.vinto.app

/**
 * The browser is the one platform where these signals genuinely exist, so it is the one that
 * actually reads them.
 *
 * Two independent signals, and either alone is enough to stay silent:
 *
 * - **Global Privacy Control** (`navigator.globalPrivacyControl`) is the live one. It is a
 *   legally recognised opt-out signal in several jurisdictions, and browsers that send it
 *   send it deliberately.
 * - **Do-Not-Track** (`navigator.doNotTrack`) is largely deprecated and widely ignored by the
 *   industry, which is precisely why it is worth honouring: a browser still sending `"1"` is
 *   one whose user went looking for the setting. It is read as a string because the values
 *   are `"1"`, `"0"`, `"unspecified"` and `null` depending on the browser, and only `"1"` is
 *   a request.
 *
 * HOSTING.md §6c binds this zone to honouring both. This is that paragraph as code.
 */
actual fun platformObjectsToTracking(): Boolean = browserObjects()

// detekt reads Kotlin, not the JavaScript below, so it cannot see this body at all. The
// suppression belongs on the function doing the interop rather than in the config.
private fun browserObjects(): Boolean = js(
    """{
      try {
        if (navigator.globalPrivacyControl === true) return true;
        var dnt = navigator.doNotTrack || window.doNotTrack || navigator.msDoNotTrack;
        return String(dnt) === '1' || String(dnt) === 'yes';
      } catch (e) {
        // A browser that will not answer is not a browser that objected.
        return false;
      }
    }""",
)
