/*
 * The portfolio's one test convention (kirunak niva/android-host-tests-prefs, D-5; its design is that task's
 * steps/S-10/design.md, section 1). Vinto takes no games-core, so this is a copy of games-core's
 * gradle/tests.gradle.kts at 8b80898a. Every module with tests applies it by one line at the END of its build file,
 * after `kotlin { }`, so its targets exist: build-logic's vinto.kmp.library (shapes, engine, bot, client, protocol),
 * shared/room and composeApp, each as
 *   apply(from = rootProject.file("gradle/tests.gradle.kts"))
 * It differs from games-core's in two places, both marked VINTO: Vinto keeps configuration cache on, so every test
 * task is marked not compatible with it (R7), and R6 does nothing on a host with no iosSimulatorArm64 target (Linux CI).
 * Take a newer games-core version by copying it again and keeping those two.
 *
 * R1  `-Pgames.suite=everyday` (the default), `deep` or `all` picks the suite; the task name still picks the platform
 *     (`desktopTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`, `wasmJsNodeTest`, ...).
 * R2  A deep test is a class whose simple name ends in `DeepTest`: everyday leaves it out, deep runs only it, all runs
 *     every test. A suite with nothing in it passes.
 * R3  On the iOS simulator everyday runs only what can differ there (D-8): the classes of the native test source sets
 *     (src/{iosTest,appleTest,nativeTest,iosSimulatorArm64Test}, one class per file) and common classes named
 *     `*EveryTargetTest` (threads, memory, formatting, floating point, platform code). The rest of commonTest runs on
 *     iOS in deep, so everyday and deep together are every test there too.
 * R4  The guard (AC-10): in everyday, a test over `games.slowSeconds` (default 10; -P, or a line in gradle.properties)
 *     fails its task, named with its time, and the same lines go to build/test-results/<task>/slow.txt, written even
 *     when the task fails on a test.
 * R5  `-Pgames.failFast=true` stops every JVM test task (desktop, jvm, Android host) at its first failure (AC-14).
 * R6  `-Pgames.iosTestBinary=release` runs `iosSimulatorArm64Test` from the RELEASE test binary (D-9). It reaches
 *     the Kotlin plugin only through its Groovy-facing API, since a script plugin does not see the plugin's classes.
 *     `games.iosTestBinary.deep=release` in a module's gradle.properties takes it for deep and all only, where the run
 *     is long enough to pay for the slower link: measured on games-core's kernel (S-12), debug 5.5 s link + 296 s run,
 *     release 18 s + 60 s; its everyday iOS suite runs in under a second, so debug's link wins there.
 *     `games.iosTestBinary=release` in a module's gradle.properties takes it for every suite, where even everyday is
 *     long: measured on music (S-13), release ran the deep suite's 261 tests in 5 min 43 s, link included, debug the
 *     everyday suite's 91 in 23 min 55 s, 11 of them over the guard. `-Pgames.iosTestBinary=debug` still wins over
 *     either line, since Gradle puts the command line's -P above a module's gradle.properties.
 * R7  Configuration cache is off in the portfolio, and this script relies on it: the guard's listener and doLast hold
 *     state. If it is ever turned on, the guard moves to a BuildService test listener. VINTO: Vinto turns it on
 *     (gradle.properties), so every test task says it is not compatible, and a build that runs tests stores no cache
 *     entry while every other build keeps the cache.
 *
 * Every property is read with findProperty, so a line in the module's own gradle.properties counts as well as -P.
 * games-core's gradle/tests-fixture/ proves the rules (its README); games-core's tools/suite.mjs is the command around
 * them.
 */

val deepSuffix = "DeepTest"
val everyTargetSuffix = "EveryTargetTest"
val testsSuite = (findProperty("games.suite") as String?) ?: "everyday"
require(testsSuite in listOf("everyday", "deep", "all")) { "games.suite is everyday, deep or all, not $testsSuite" }
val testsSlowSeconds = ((findProperty("games.slowSeconds") as String?) ?: "10").toDouble()
val testsFailFast = (findProperty("games.failFast") as String?).toBoolean()
val testsIosBinary = (findProperty("games.iosTestBinary") as String?)
    ?: (if (testsSuite == "everyday") null else findProperty("games.iosTestBinary.deep") as String?)
    ?: "debug"
require(testsIosBinary in listOf("debug", "release")) { "games.iosTestBinary is debug or release, not $testsIosBinary" }

/** The classes of the native test source sets, by file name: games/core/x/FooTest.kt is games.core.x.FooTest. */
val nativeTestClasses: List<String> =
    listOf("iosTest", "appleTest", "nativeTest", "iosSimulatorArm64Test").map { file("src/$it/kotlin") }
        .filter { it.isDirectory }
        .flatMap { dir ->
            fileTree(dir) { include("**/*Test.kt") }.files.map {
                it.relativeTo(dir).path.removeSuffix(".kt").replace(File.separatorChar, '.')
            }
        }
        .sorted()

fun AbstractTestTask.isSimulatorTest() = name.endsWith("SimulatorArm64Test")

fun AbstractTestTask.pickSuite() {
    filter.isFailOnNoMatchingTests = false
    inputs.property("games.suite", testsSuite)
    val simulator = isSimulatorTest()
    when (testsSuite) {
        "everyday" -> {
            if (simulator) {
                nativeTestClasses.forEach { filter.includeTestsMatching(it) }
                filter.includeTestsMatching("*$everyTargetSuffix")
            }
            filter.excludeTestsMatching("*$deepSuffix")
        }
        "deep" -> if (simulator) {
            nativeTestClasses.filterNot { it.endsWith(deepSuffix) }.forEach { filter.excludeTestsMatching(it) }
            filter.excludeTestsMatching("*$everyTargetSuffix")
        } else {
            filter.includeTestsMatching("*$deepSuffix")
        }
    }
}

fun seconds(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)

fun AbstractTestTask.guardSlowTests(limit: Double) {
    inputs.property("games.slowSeconds", limit)
    val slow = mutableListOf<String>()
    val slowFile = reports.junitXml.outputLocation.file("slow.txt")
    doFirst {
        slow.clear()
        slowFile.get().asFile.delete()
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit

        override fun beforeTest(testDescriptor: TestDescriptor) = Unit

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            val took = (result.endTime - result.startTime) / 1000.0
            if (took > limit) {
                slow += "${testDescriptor.className}.${testDescriptor.name} took ${seconds(took)} s " +
                    "(the limit is ${seconds(limit)} s, games.slowSeconds): " +
                    "mark it *$deepSuffix or give it a short everyday twin"
            }
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null || slow.isEmpty()) return
            slowFile.get().asFile.run {
                parentFile.mkdirs()
                writeText(slow.joinToString("\n", postfix = "\n"))
            }
        }
    })
    doLast {
        if (slow.isNotEmpty()) {
            throw GradleException(
                "${slow.size} test(s) over ${seconds(limit)} s in the everyday suite of $path:\n" +
                    slow.joinToString("\n"),
            )
        }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    // VINTO: R7, the guard's state cannot go into a configuration cache entry.
    notCompatibleWithConfigurationCache("the test convention's suite guard holds state (gradle/tests.gradle.kts, R7)")
    pickSuite()
    if (testsSuite == "everyday") guardSlowTests(testsSlowSeconds)
}

if (testsFailFast) {
    tasks.withType<Test>().configureEach { failFast = true }
}

/** R6: the simulator's test run takes the release test binary, made beside the debug one. */
fun useReleaseIosTestBinary() {
    val kotlinExtension = extensions.findByName("kotlin")
        ?: error("games.iosTestBinary=release needs the Kotlin Multiplatform plugin")
    val targets = kotlinExtension.withGroovyBuilder { getProperty("targets") } as NamedDomainObjectCollection<*>
    // VINTO: the Apple targets exist only on a Mac (build-logic's vinto.kmp.library), so elsewhere there is nothing to
    // switch, and a module's games.iosTestBinary.deep line must not break a Linux build.
    val target = targets.findByName("iosSimulatorArm64") ?: return
    val pluginClass = generateSequence<Class<*>>(kotlinExtension.javaClass) { it.superclass }
        .first { !it.name.endsWith("_Decorated") }
    val release = Class.forName("org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType", true, pluginClass.classLoader)
        .enumConstants.first { (it as Enum<*>).name == "RELEASE" }
    val binaries = target.withGroovyBuilder { getProperty("binaries") }
    binaries.withGroovyBuilder { "test"(listOf(release), Action<Any> { }) }
    val executable = binaries.withGroovyBuilder { "getTest"(release) }
    val testRuns = target.withGroovyBuilder { getProperty("testRuns") } as NamedDomainObjectCollection<*>
    testRuns.getByName("test").withGroovyBuilder { "setExecutionSourceFrom"(executable) }
}

if (testsIosBinary == "release") useReleaseIosTestBinary()
