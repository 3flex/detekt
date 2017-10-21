package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.deprecatedByOverriddenMessage
import org.jetbrains.kotlin.resolve.getDeprecations

class OverridingDeprecatedMember(config: Config) : Rule(config) {

	override val issue = Issue("OverridingDeprecatedMember",
			Severity.Warning,
			"This declaration or property accessor overrides a deprecated member.",
			Debt.TEN_MINS)

	override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
		super.visitNamedDeclaration(declaration)
		registerProblemIfNeeded(declaration)
	}

	override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
		super.visitPropertyAccessor(accessor)
		registerProblemIfNeeded(accessor)
	}

	private fun registerProblemIfNeeded(declaration: KtDeclaration) {
		val accessorDescriptor = declaration.resolveToDescriptorIfAny(bindingContext) as? CallableMemberDescriptor
				?: return

		accessorDescriptor.getDeprecations(LanguageVersionSettingsImpl.DEFAULT)
				.firstOrNull()
				?.deprecatedByOverriddenMessage()
				?.let { report(CodeSmell(issue, Entity.from(declaration))) }
	}

	private fun KtDeclaration.resolveToDescriptorIfAny(bindingContext: BindingContext): DeclarationDescriptor? {
		return bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, this)
	}

}
