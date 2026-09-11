# Golden screenshots

The images `ScreenshotTest` compares the screens against. There are ten: `home`, `settings`,
`table`, `table-wide` and `plan`, each in `-light` and `-dark`.

**They are committed.** The whole point of a golden is that a change to it turns up in a review
as a picture somebody can look at; one that lives only on the machine that wrote it is a check
nobody else can ever run, and a screen can then drift for months with every test still green.

They are **generated, not drawn**: run the suite and any missing golden is written from the
live rendering, so bootstrapping or accepting a change is the same two commands —

```sh
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest   # writes what's missing
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest --rerun   # proves it's stable
```

To accept an intended visual change, delete the affected `.png`, run those two, and **look at
the result** before committing it — that look is the only thing standing between a deliberate
change and a regression, and no assertion can do it for you.

On a mismatch the test writes the new rendering beside the golden as `<name>.actual.png` for
eyeballing — those files are working debris, never committed (see `.gitignore` here).

**CI does not run this suite**, and that is not laziness. Font rasterization differs slightly
between JVMs — the comparison tolerates a fringe of glyph-edge pixels, but a runner with
different fonts would still disagree — and a fresh runner with no goldens would simply write
its own and pass, asserting nothing. So the suite runs on a person's machine, and the pictures
it produces travel in the repository where a reviewer can see them.
