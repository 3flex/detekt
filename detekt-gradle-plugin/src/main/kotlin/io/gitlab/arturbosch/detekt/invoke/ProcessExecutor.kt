package io.gitlab.arturbosch.detekt.invoke

import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

object ProcessExecutor {
    fun startProcess(args: Array<String>) {
        val process = Runtime.getRuntime().exec(args)
        val logger = Logging.getLogger(this::class.java)

        process.inputStream.bufferedReader().use {
            val inputs = it.readLines().joinToString("\n")
            logger.debug(inputs)
        }

        process.errorStream.bufferedReader().use {
            val errors = it.readLines().joinToString("\n")
            if (errors.isNotEmpty()) {
                throw GradleException(errors)
            }
        }

        process.destroy()
    }
}
