# App Links — the two files that make an https invitation open the app

They live in `composeApp/src/wasmJsMain/resources/.well-known/`, which every website deploy
publishes. This document is here rather than beside them because that directory IS the deployed
site: a README next to them would be served at `/.well-known/README.md`, which is both clutter
and a needlessly public account of how the app is signed.

Both are served from `vinto.kupalinka.app` by the website Worker, because that is the host an
invitation names (`INVITE_HOST`). Neither is read by the website itself: they are read by
Android and by iOS, once each, to decide whether this app is allowed to claim links on this
domain. Until they existed, an `https://vinto.kupalinka.app/r/7KQ2MP` invitation opened the
website instead of the app on both platforms — which is why the `vinto://` scheme is also
wired, and works with no hosted file at all.

## `assetlinks.json` — Android App Links

Answers the check `android:autoVerify="true"` triggers (`AndroidManifest.xml`). Android fetches
this file and opens the app without a chooser only if it names the app's signing certificate.

**The fingerprint currently in the file is the UPLOAD key's, and that is not sufficient on its
own for a Play install.** With Play App Signing — mandatory for new apps — Google re-signs the
bundle with an *app signing key* it holds and never releases, so the certificate on a
Play-installed app is Google's, not ours. The upload fingerprint is the right answer for a
directly installed APK (`installRelease`, a file sent to a tester) and the wrong one for
everything that comes through the store.

So this file is **half done, deliberately**, and finishing it needs no code:

1. Create the app in the Play Console and upload the first bundle (`bundleRelease`).
2. Play Console → the app → **Test and release → Setup → App signing**.
3. Copy the **App signing key certificate**'s SHA-256 fingerprint.
4. Add it to `sha256_cert_fingerprints` **beside** the existing one — an array, not a
   replacement. Keeping both means sideloaded builds and store builds both verify.
5. Redeploy the website (`deploy-web.yml`). No app build is involved, which is the whole point
   of hosting this rather than compiling it in.

Verify with Google's own checker rather than by eye, because a fingerprint that is right except
for one byte fails silently and looks identical:

```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://vinto.kupalinka.app&relation=delegate_permission/common.handle_all_urls
```

## `apple-app-site-association` — iOS Universal Links

Answers the check the `com.apple.developer.associated-domains` entitlement triggers
(`iosApp/iosApp/iosApp.entitlements`, `applinks:vinto.kupalinka.app`).

Complete as it stands: the `appIDs` entry is `<team id>.<bundle id>`, both of which are already
fixed (`JNHFD8PCM8`, `app.kupalinka.vinto`), and Apple has no equivalent of the signing-key
problem above.

Two things about it that are easy to get wrong:

- **No file extension, and it must be served as `application/json`.** Cloudflare infers a
  content type from the extension, and there is none here, so `_headers` sets it explicitly.
  Without that iOS fetches the file, fails to parse it, and reports nothing.
- **It claims `/r/*` and nothing else.** Claiming `/` would hand the app every page of the
  website — including the marketing pages it cannot render — so a tap on a shared link to the
  home page would open a game instead of a web page. `/r/*` is exactly the shape
  `roomCodeFrom` understands.

Apple caches this file through its own CDN, so a change can take a day to reach devices unless
the app is built with the `developer` mode entitlement. That is normal and not a fault.

## Verifying either one from a device

Neither can be tested in a simulator or by a unit test — both are a real device asking a real
host, which is why `ship-and-operate` 1.3 needs a phone of each kind. On Android,
`adb shell pm get-app-links app.kupalinka.vinto` prints the verification state per domain.
