package io.gitlab.arturbosch.detekt.api

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.TopDownAnalysisContext

/**
 * A rule set is a collection of rules and must be defined within a rule set provider implementation.
 *
 * @author Artur Bosch
 */
class RuleSet(val id: String, val rules: List<BaseRule>) {

	init {
		validateIdentifier(id)
	}

	/**
	 * Visits given file with all rules of this rule set, returning a list
	 * of all code smell findings.
	 */
	fun accept(file: KtFile, analysisContext: TopDownAnalysisContext): List<Finding> {
		val findings: MutableList<Finding> = mutableListOf()
		rules.forEach {
			it.visitFile(file, analysisContext)
			findings += it.findings
		}
		return findings
	}

}
