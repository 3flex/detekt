package dev.detekt.rules.style

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.ValueWithReason
import dev.detekt.api.config
import dev.detekt.api.valuesWithReason
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression

/**
 * This rule allows to set a list of forbidden opt-ins. This can be used to avoid opting into an api by accident.
 * By default, the list of forbidden opt-ins is empty.
 *
 * Marker classes can be configured by either their simple name or their fully qualified name. The rule
 * also detects opt-ins that reference the marker class with a fully qualified name in source.
 */
class ForbiddenOptIn(config: Config) :
    Rule(
        config,
        "Using this opt-in is forbidden."
    ),
    RequiresAnalysisApi {

    @Configuration(
        "List of marker classes that are forbidden to be used."
    )
    private val markerClasses: Map<String, ValueWithReason> by config(valuesWithReason()) { list ->
        list.associateBy { it.value }
    }

    override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotation)
        if (markerClasses.isEmpty()) {
            return
        }

        val forbidden = analyze(annotation) {
            if (annotation.typeReference?.type?.symbol?.classId != optInClassId) {
                return
            }
            annotation.valueArguments.mapNotNull { arg ->
                val classLiteral = arg.getArgumentExpression() as? KtClassLiteralExpression
                val classId = classLiteral?.receiverExpression?.expressionType?.symbol?.classId
                    ?: return@mapNotNull null
                val fqName = classId.asSingleFqName().asString()
                val simpleName = classId.shortClassName.asString()
                markerClasses[fqName] ?: markerClasses[simpleName]
            }
        }

        forbidden.forEach { entry ->
            val message = if (entry.reason != null) {
                "The opt-in `${entry.value}` has been forbidden: ${entry.reason}"
            } else {
                "The opt-in `${entry.value}` has been forbidden in the detekt config."
            }
            report(Finding(Entity.from(annotation), message))
        }
    }

    companion object {
        private val optInClassId: ClassId = ClassId.fromString("kotlin/OptIn")
    }
}
