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
 * The self-play tournament is a manually-run gate.
 *
 * `TournamentTest` plays thirty-six whole MCTS games — minutes, not seconds — to compare the
 * bot against the numbers committed in `fixtures/bot/self-play-baseline.json`. That is worth
 * paying when a heuristic changes and is not worth paying on every push, so it is excluded
 * from `jvmTest` unless asked for. `SelfPlayGateTest`, which is the *legality* gate and the
 * one that matters for a release, keeps running every time.
 *
 * ```sh
 * ./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament        # compare
 * ./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament=write  # regenerate
 * ```
 */
tasks.withType<Test>().configureEach {
    val tournament = providers.gradleProperty("tournament")
    if (!tournament.isPresent) {
        filter {
            excludeTestsMatching("game.vinto.bot.TournamentTest")
            isFailOnNoMatchingTests = false
        }
    } else {
        // The point of running it by hand is to read the table it prints.
        testLogging.showStandardStreams = true
        if (tournament.get() == "write") systemProperty("vinto.tournament.write", "true")
    }

    // Retailing the parity corpus is the same shape of decision and a larger one: it rewrites a
    // frozen artefact, so it never runs unasked. `fixtures/recordings/README.md` is the argument
    // it has to answer to.
    val corpus = providers.gradleProperty("corpus")
    if (!corpus.isPresent) {
        filter {
            excludeTestsMatching("game.vinto.bot.RegenerateCorpusTailsTest")
            isFailOnNoMatchingTests = false
        }
    } else {
        testLogging.showStandardStreams = true
        if (corpus.get() == "write") systemProperty("vinto.corpus.write", "true")
    }
}
