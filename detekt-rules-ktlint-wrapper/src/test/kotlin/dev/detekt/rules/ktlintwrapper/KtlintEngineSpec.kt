package dev.detekt.rules.ktlintwrapper

import dev.detekt.api.Config
import dev.detekt.api.modifiedText
import dev.detekt.rules.ktlintwrapper.wrappers.NoLineBreakBeforeAssignment
import dev.detekt.rules.ktlintwrapper.wrappers.NoSemicolons
import dev.detekt.test.FakeLanguageVersionSettings
import dev.detekt.test.utils.compileContentForTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KtlintEngineSpec {

    @Test
    fun `context is shared across visits on the same KtFile`() {
        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineSpecTest.kt")
        NoLineBreakBeforeAssignment(Config.empty).visitFile(ktFile, FakeLanguageVersionSettings())

        val first = KtlintEngine.contextFor(ktFile)
        val second = KtlintEngine.contextFor(ktFile)

        assertThat(first).isSameAs(second)
    }

    @Test
    fun `different KtFiles yield independent contexts`() {
        val fileA = compileContentForTest("fun main()\n= Unit", "EngineSpecA.kt")
        val fileB = compileContentForTest("fun main()\n= Unit", "EngineSpecB.kt")
        val rule = NoLineBreakBeforeAssignment(Config.empty)
        rule.visitFile(fileA, FakeLanguageVersionSettings())
        rule.visitFile(fileB, FakeLanguageVersionSettings())

        val contextA = KtlintEngine.contextFor(fileA)
        val contextB = KtlintEngine.contextFor(fileB)

        assertThat(contextA).isNotSameAs(contextB)
    }

    @Test
    fun `findings reach the rule via the shared engine walk`() {
        // Smoke test for the whole pipeline: KtlintRule.visit -> KtlintEngine.contextFor ->
        // single shared walk -> emit findings tagged with the rule's RuleId. The wrapped
        // ktlint rule reports a single violation on `fun main()\n= Unit`.
        val findings = NoLineBreakBeforeAssignment(Config.empty).lint("fun main()\n= Unit")

        assertThat(findings).hasSize(1)
    }

    @Test
    fun `engine reads from modifiedText when a prior autocorrect has set it`() {
        // Source has a violation; modifiedText overrides it with a clean version.
        // The engine should walk modifiedText, not the original text, and emit no findings.
        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineSpecModifiedText.kt")
        ktFile.modifiedText = "fun main() = Unit\n"

        val findings = NoLineBreakBeforeAssignment(Config.empty)
            .visitFile(ktFile, FakeLanguageVersionSettings())

        assertThat(findings).isEmpty()
    }

    @Test
    fun `a later visit on a reused KtFile must not be served a stale context after modifiedText changes`() {
        // Reproduces the cross-test flake: the engine seeds remainingVisits with the number of
        // rules participating in the shared walk (active.size), but only decrements it for rules
        // that actually call visit(). Whenever more rules are registered than visit a given file,
        // the eviction counter can never reach zero, the per-file context is never evicted, and a
        // later visit on the same KtFile is served the stale cached findings instead of rebuilding
        // from the (now changed) modifiedText.

        // Trigger a second, unrelated rule on a different file so the engine's registry holds two
        // active rules. This guarantees remainingVisits starts at >= 2 regardless of test ordering.
        val otherFile = compileContentForTest("fun other() = Unit\n", "EngineStaleOther.kt")
        NoSemicolons(Config.empty).visitFile(otherFile, FakeLanguageVersionSettings())

        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineStaleContext.kt")
        val rule = NoLineBreakBeforeAssignment(Config.empty)

        // First pass: the file still has the violation, so one finding is expected. Building the
        // context seeds remainingVisits = 2; this single rule then decrements it to 1 (not evicted).
        assertThat(rule.visitFile(ktFile, FakeLanguageVersionSettings())).hasSize(1)

        // Simulate a prior autocorrect having cleaned the file.
        ktFile.modifiedText = "fun main() = Unit\n"

        // The second pass should re-read modifiedText (now clean) and report nothing. With the
        // never-evicted stale context it is instead served the original dirty findings.
        val secondPass = rule.visitFile(ktFile, FakeLanguageVersionSettings())

        assertThat(secondPass).isEmpty()
    }

    @Test
    fun `ruleDoneWithFile evicts the per-file context once remaining visits reach zero`() {
        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineSpecEviction.kt")
        NoLineBreakBeforeAssignment(Config.empty).visitFile(ktFile, FakeLanguageVersionSettings())

        val context = KtlintEngine.contextFor(ktFile)
        while (context.remainingVisits.get() > 0) {
            KtlintEngine.ruleDoneWithFile(ktFile, context)
        }

        // After draining, a subsequent contextFor must build a fresh context (different identity).
        val rebuilt = KtlintEngine.contextFor(ktFile)
        assertThat(rebuilt).isNotSameAs(context)
    }
}
