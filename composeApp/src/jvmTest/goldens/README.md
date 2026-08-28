# Golden screenshots

The images `ScreenshotTest` compares the screens against. There are eight: `home`,
`settings`, `table` and `table-wide`, each in `-light` and `-dark`.

They are **generated, not drawn**: run the suite and any missing golden is written from the
live rendering, so bootstrapping or accepting a change is the same two commands —

```sh
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest   # writes what's missing
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest --rerun   # proves it's stable
```

To accept an intended visual change, delete the affected `.png` and run twice as above. On a
mismatch the test writes the new rendering beside the golden as `<name>.actual.png` for
eyeballing — those files are working debris, never committed (see `.gitignore` here).

Font rasterization differs slightly between JVMs, which the comparison tolerates
(a fringe of glyph-edge pixels); goldens are therefore generated and kept by the
maintainer on the machine that runs the suite, not in a container that cannot.
