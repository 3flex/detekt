package io.gitlab.arturbosch.detekt.core

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import java.io.File

class AnalysisEnvironment : Disposable {
    val configuration = CompilerConfiguration()

    init {
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "module for detekt static analysis")
    }

    fun createCoreEnvironment(files: List<KtFile>): BindingContext {
        val environment = KotlinCoreEnvironment.createForProduction(
            this, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        return TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            environment.project, files, NoScopeRecordCliBindingTrace(),
            environment.configuration, environment::createPackagePartProvider, ::FileBasedDeclarationProviderFactory
        ).bindingContext
    }

    fun addClasspaths(paths: List<File>) {
        configuration.addJvmClasspathRoots(paths)
    }

    fun addClasspath(path: File) {
        configuration.addJvmClasspathRoot(path)
    }

    fun addSources(list: List<String>) {
        configuration.addKotlinSourceRoots(list)
    }

    override fun dispose() {
        Disposer.dispose(this)
    }
}
