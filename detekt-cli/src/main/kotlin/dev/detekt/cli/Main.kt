@file:JvmName("Main")

package dev.detekt.cli

import dev.detekt.api.internal.whichKotlin
import dev.detekt.cli.runners.ConfigExporter
import dev.detekt.cli.runners.Executable
import dev.detekt.cli.runners.Runner
import dev.detekt.cli.runners.VersionPrinter
import dev.detekt.tooling.api.AnalysisResult
import dev.detekt.tooling.api.InvalidConfig
import dev.detekt.tooling.api.IssuesFound
import dev.detekt.tooling.api.UnexpectedError
import dev.detekt.tooling.internal.NotApiButProbablyUsedByUsers
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val result = CliRunner().run(args)
    @Suppress("ForbiddenMethodCall")
    when (val error = result.error) {
        is InvalidConfig, is IssuesFound -> println(error.message)

        is UnexpectedError -> {
            when (val cause = error.cause) {
                is HelpRequest -> {
                    println(cause.usageText)
                    exitProcess(0)
                }

                is HandledArgumentViolation -> {
                    println(cause.message)
                    println(cause.usageText)
                }

                else -> cause.printStackTrace()
            }
        }

        else -> Unit // print nothing extra when there is no error
    }
    exitProcess(result.exitCode())
}

/**
 * The reflective entry point used by detekt's Gradle plugin (`DefaultCliInvoker`) to invoke the CLI.
 * The signature must stay stable so that any 2.x Gradle plugin can invoke any 2.x CLI on the classpath.
 * It is not intended to be called by users; use `DetektCli.load()` from detekt-tooling instead.
 */
@NotApiButProbablyUsedByUsers
fun buildRunner(args: Array<String>, outputPrinter: PrintStream, errorPrinter: PrintStream): Executable {
    check(KotlinCompilerVersion.VERSION == whichKotlin()) {
        """
            detekt was compiled with Kotlin ${whichKotlin()} but is currently running with ${KotlinCompilerVersion.VERSION}.
            This is not supported. See https://detekt.dev/docs/gettingstarted/gradle#dependencies for more information.
        """.trimIndent()
    }
    val arguments = parseArguments(args)
    return when {
        arguments.showVersion -> VersionPrinter(outputPrinter)
        arguments.generateConfig != null -> ConfigExporter(arguments, outputPrinter)
        else -> Runner(arguments, outputPrinter, errorPrinter)
    }
}

@Suppress("detekt.MagicNumber")
internal fun AnalysisResult.exitCode(): Int =
    when (error) {
        is UnexpectedError -> 1
        is IssuesFound -> 2
        is InvalidConfig -> 3
        null -> 0
    }
