package io.gitlab.arturbosch.detekt.core

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer

class DetektMessageCollector : MessageCollector {
	private var hasErrors = false
	private var reportWarnings = true

	private fun reportIssue(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
		hasErrors = true
		println(MessageRenderer.PLAIN_FULL_PATHS.render(severity, message, location))
	}

	override fun clear() {
		hasErrors = false
	}

	override fun hasErrors(): Boolean = hasErrors

	override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
		when (severity) {
			CompilerMessageSeverity.EXCEPTION,
			CompilerMessageSeverity.ERROR,
			CompilerMessageSeverity.STRONG_WARNING -> reportIssue(severity, message, location)
			CompilerMessageSeverity.WARNING -> if (reportWarnings) reportIssue(severity, message, location) else return
			else -> return
		}
	}

}
