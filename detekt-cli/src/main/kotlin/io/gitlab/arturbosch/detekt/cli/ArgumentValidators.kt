package io.gitlab.arturbosch.detekt.cli

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmTarget.Companion.SUPPORTED_VERSIONS_DESCRIPTION

class JvmTargetValidator : IParameterValidator {
    override fun validate(name: String, value: String) {
        if (JvmTarget.fromString(value) == null) {
            throw ParameterException("Value passed to $name must be in range $SUPPORTED_VERSIONS_DESCRIPTION")
        }
    }
}
