plugins {
    id("vinto.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:shapes"))
            api(project(":shared:engine"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}


/**
 * The self-play tournament and the corpus regeneration are deep tests, run by hand.
 *
 * `TournamentDeepTest` plays thirty-six whole MCTS games (minutes, not seconds) to compare the
 * bot against the numbers committed in `fixtures/bot/self-play-baseline.json`. That is worth
 * paying when a heuristic changes and is not worth paying on every push, so, like every
 * `*DeepTest`, the everyday suite leaves it out and `-Pgames.suite=deep` runs it (the test
 * convention, gradle/tests.gradle.kts). The *legality* gate, the one that matters for a
 * release, is `SelfPlayGateDeepTest`'s twelve `moderate` games in the deep suite, and
 * `SelfPlayGateTest` asks the same questions of one `easy` game every time.
 *
 * ```sh
 * ./gradlew :shared:bot:jvmTest -Pgames.suite=deep --tests '*TournamentDeepTest*' -Ptournament        # compare
 * ./gradlew :shared:bot:jvmTest -Pgames.suite=deep --tests '*TournamentDeepTest*' -Ptournament=write  # regenerate
 * ```
 *
 * The two properties below no longer pick what runs; they make a run by hand print its table,
 * and `=write` lets it rewrite what it compares against.
 */
tasks.withType<Test>().configureEach {
    val tournament = providers.gradleProperty("tournament")
    if (tournament.isPresent) {
        // The point of running it by hand is to read the table it prints.
        testLogging.showStandardStreams = true
        if (tournament.get() == "write") systemProperty("vinto.tournament.write", "true")
    }

    // Retailing the parity corpus is the same shape of decision and a larger one: it rewrites a
    // frozen artefact, so it never writes unasked. `fixtures/recordings/README.md` is the
    // argument it has to answer to. `RegenerateCorpusTailsDeepTest` only prints without `=write`.
    val corpus = providers.gradleProperty("corpus")
    if (corpus.isPresent) {
        testLogging.showStandardStreams = true
        if (corpus.get() == "write") systemProperty("vinto.corpus.write", "true")
    }
}
