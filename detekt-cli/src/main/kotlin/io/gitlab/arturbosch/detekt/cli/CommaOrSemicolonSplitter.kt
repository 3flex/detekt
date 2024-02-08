package io.gitlab.arturbosch.detekt.cli

import com.beust.jcommander.converters.IParameterSplitter

class CommaOrSemicolonSplitter : IParameterSplitter {
    override fun split(value: String): List<String> = value.split(SEPARATOR_COMMA, SEPARATOR_SEMICOLON)
}
