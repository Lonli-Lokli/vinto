import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The shape every shared module has: five targets, one JDK, one set of Android levels.
 *
 * `shapes`, `engine`, `bot`, `protocol` and `client` are the same module five times over —
 * the same `jvm()`, the same `androidTarget`, the same `js`, the same `wasmJs`, the same
 * host-guarded Apple targets, the same `compileSdk`. Repeating that is not just noise: it is
 * the arrangement in which one of six modules quietly ends up different, which is exactly
 * what happened — `shared:room` was the only module that never pinned its `jvmTarget`, and
 * nothing could have caught it because there was nowhere for the rule to live.
 *
 * `room` and `worker` do not use this. They are server-side and genuinely have fewer targets
 * (no phone hosts a room), so they keep their own files rather than being bent to fit.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun version(name: String): String = libs.findVersion(name).get().requiredVersion

/**
 * Kotlin/Native cannot build Apple targets anywhere but macOS, so they are declared behind a
 * host check. On Windows or Linux the build simply has fewer targets.
 *
 * It says so out loud rather than silently building less than you asked for. Do not make
 * these unconditional: on a non-Mac host that is a hard toolchain failure, not a warning —
 * and a `commonMain` change that breaks iOS stays invisible until a Mac compiles it.
 */
val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

/**
 * Mocha's default is two seconds, which is a web framework's idea of a slow test rather than
 * a Monte Carlo search's. A session test plays a whole round, and a round is a hundred-odd
 * MCTS searches — up to 1.6 s each on the slower targets (`docs/kotlin/PLATFORM-GATE.md`
 * 2a.1b). `shared:client` already carried this number for that reason; `shared:bot` did not,
 * and `OpponentKnowledgeFlowTest` duly timed out on JS while passing on the JVM. Having it
 * once is the point.
 *
 * It is a hang detector, not a performance claim.
 */
val SLOW_TEST_TIMEOUT = "600s"

if (!isMacOs) {
    logger.warn(
        "${project.path} — building WITHOUT the Apple targets on ${System.getProperty("os.name")}. " +
            "They require macOS; shared-code breakage on iOS will not surface here.",
    )
}

kotlin {
    // jvm     — tests and tooling; there is no JVM server (design D1).
    // android — the Android app.
    // js      — the Cloudflare Worker bundle.
    // wasmJs  — the Compose web client.
    // ios     — the iOS app, on macOS only.
    jvm()

    /**
     * `kotlin { android { } }`, not `androidTarget()` plus a top-level `android { }`.
     *
     * Since AGP 9.0 the `com.android.library` plugin refuses to sit beside the Kotlin
     * Multiplatform plugin at all, and this is the replacement it names. It folds the whole
     * Android configuration into the `kotlin { }` block, which is why the `android { }` block
     * that used to follow this one is gone.
     *
     * The namespace is derived rather than declared five times. Every shared module already
     * spelled it `game.vinto.<name>`, and a convention that has to be repeated per module is
     * the arrangement in which one of them ends up different — the same argument this whole
     * file exists for.
     */
    android {
        namespace = "game.vinto.${project.name}"
        compileSdk = version("androidCompileSdk").toInt()
        minSdk = version("androidMinSdk").toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js {
        binaries.library()
        nodejs {
            testTask {
                useMocha { timeout = SLOW_TEST_TIMEOUT }
            }
        }
        useEsModules()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
        nodejs {
            testTask {
                useMocha { timeout = SLOW_TEST_TIMEOUT }
            }
        }
    }

    if (isMacOs) {
        iosArm64()
        iosSimulatorArm64()
        // Intel Macs, and the simulators that run on them. One line, and the alternative is
        // a contributor on that hardware being unable to run the iOS half at all.
        iosX64()
    }
}
