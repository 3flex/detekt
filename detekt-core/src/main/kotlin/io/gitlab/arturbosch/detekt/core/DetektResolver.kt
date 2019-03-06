package io.gitlab.arturbosch.detekt.core

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

class DetektResolver(
    val classpath: List<String>,
    private val sourcePaths: List<String>,
    val providers: List<RuleSetProvider>,
    val config: Config
) {

    fun generate(files: List<KtFile>): BindingContext {
        val environment = createAnalysisEnvironment(sourcePaths)
        return try {
            environment.createCoreEnvironment(files)
        } finally {
            Disposer.dispose(environment)
        }
    }

    private fun createAnalysisEnvironment(sourcePaths: List<String>): AnalysisEnvironment {
        val environment = AnalysisEnvironment()

        environment.apply {
            addClasspaths(PathUtil.getJdkClassesRootsFromCurrentJre())
            for (element in classpath) {
                addClasspath(File(element))
            }

            addSources(sourcePaths)
        }

        return environment
    }
}
