package dev.detekt.rules.ktlintwrapper

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * [KtlintEngine] is a process-wide singleton: its rule registry and per-file context cache persist
 * for the lifetime of the test JVM. Without isolation, rules triggered (and contexts cached) by one
 * test bleed into the next, producing order-dependent, intermittent failures. This extension resets
 * the engine before every test so each starts from a clean slate.
 *
 * Registered via `META-INF/services` and JUnit's extension auto-detection (see
 * `junit-platform.properties`), so it applies to all tests in this module without per-class wiring.
 */
class KtlintEngineResetExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        KtlintEngine.reset()
    }
}
