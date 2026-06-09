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
        // Regression test: when the same KtFile is visited again after its effective text changed
        // (e.g. a prior autocorrect cleaned it), the engine must rebuild rather than serve the
        // findings cached from the previous, now-stale text. A second unrelated rule is triggered
        // so more than one rule is registered, mirroring a real run where many rules share the
        // per-file walk.
        val otherFile = compileContentForTest("fun other() = Unit\n", "EngineStaleOther.kt")
        NoSemicolons(Config.empty).visitFile(otherFile, FakeLanguageVersionSettings())

        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineStaleContext.kt")
        val rule = NoLineBreakBeforeAssignment(Config.empty)

        // First pass: the file still has the violation, so one finding is expected.
        assertThat(rule.visitFile(ktFile, FakeLanguageVersionSettings())).hasSize(1)

        // Simulate a prior autocorrect having cleaned the file.
        ktFile.modifiedText = "fun main() = Unit\n"

        // The second pass must re-read modifiedText (now clean) and report nothing.
        val secondPass = rule.visitFile(ktFile, FakeLanguageVersionSettings())

        assertThat(secondPass).isEmpty()
    }

    @Test
    fun `a changed file rebuilds the context while an unchanged file reuses it`() {
        val ktFile = compileContentForTest("fun main()\n= Unit", "EngineSpecRebuild.kt")
        NoLineBreakBeforeAssignment(Config.empty).visitFile(ktFile, FakeLanguageVersionSettings())

        val context = KtlintEngine.contextFor(ktFile)
        // Unchanged content: the cached context is reused.
        assertThat(KtlintEngine.contextFor(ktFile)).isSameAs(context)

        // Changed content: the stale context is dropped and a fresh one is built.
        ktFile.modifiedText = "fun main() = Unit\n"
        assertThat(KtlintEngine.contextFor(ktFile)).isNotSameAs(context)
    }
}
