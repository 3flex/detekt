package io.gitlab.arturbosch.detekt.api

import org.jetbrains.kotlin.psi.KtFile

/**
 * A context describes the storing and reporting mechanism of [Finding]'s inside a [Rule].
 * Additionally it handles suppression and aliases management.
 *
 * The detekt engine retrieves the findings after each [KtFile] visit and resets the context
 * before the next [KtFile].
 *
 * @author Artur Bosch
 * @author Marvin Ramin
 */
interface Context {
    val findings: List<Finding>
    fun report(finding: Finding, aliases: Set<String> = emptySet())
    fun report(findings: List<Finding>, aliases: Set<String> = emptySet())
    fun clearFindings()
}
