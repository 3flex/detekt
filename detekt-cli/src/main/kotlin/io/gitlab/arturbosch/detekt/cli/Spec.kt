package io.gitlab.arturbosch.detekt.cli

import io.github.detekt.tooling.api.spec.ProcessingSpec
import io.github.detekt.tooling.api.spec.RulesSpec
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSet

internal fun CliArgs.createSpec(output: Appendable, error: Appendable): ProcessingSpec {
    val args = this
    return ProcessingSpec {
        logging {
            debug = args.debug
            outputChannel = output
            errorChannel = error
        }

        project {
            basePath = args.basePath
            inputPaths = args.input
            excludes = args.excludes?.let(::asPatterns).orEmpty()
            includes = args.includes?.let(::asPatterns).orEmpty()
        }

        rules {
            autoCorrect = args.autoCorrect
            activateAllRules = args.allRules
            failurePolicy = args.failurePolicy
            excludeCorrectable = false // not yet supported; loaded from config
            runPolicy = args.toRunPolicy()
        }

        baseline {
            path = args.baseline
            shouldCreateDuringAnalysis = args.createBaseline
        }

        config {
            useDefaultConfig = args.buildUponDefaultConfig
            shouldValidateBeforeAnalysis = null
            configPaths = config
            resources = configResource
        }

        execution {
            parallelParsing = args.parallel
            parallelAnalysis = args.parallel
        }

        extensions {
            disableDefaultRuleSets = args.disableDefaultRuleSets
            fromPaths { args.plugins }
        }

        reports {
            args.reportPaths.forEach {
                report { it.kind to it.path }
            }
        }

        compiler {
            jvmTarget = args.jvmTarget.toString()
            languageVersion = args.languageVersion?.versionString
            classpath = args.classpath?.trim()
            jdkHome = args.jdkHome
        }
    }
}

private fun asPatterns(rawValue: String): List<String> = rawValue.trim()
    .splitToSequence(",", ";")
    .filter { it.isNotBlank() }
    .map { it.trim() }
    .toList()

private fun CliArgs.toRunPolicy(): RulesSpec.RunPolicy {
    val parts = runRule?.split(":") ?: return RulesSpec.RunPolicy.NoRestrictions
    require(parts.size == 2) { "Pattern 'RuleSetId:RuleId' expected." }
    return RulesSpec.RunPolicy.RestrictToSingleRule(RuleSet.Id(parts[0]), Rule.Id(parts[1]))
}
