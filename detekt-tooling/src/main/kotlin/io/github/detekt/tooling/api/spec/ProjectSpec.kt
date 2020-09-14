package io.github.detekt.tooling.api.spec

import java.nio.file.Path

/**
 * Project specific configuration and paths.
 */
interface ProjectSpec {

    /**
     * Base path of your project. Used to resolve relative paths for path matching and filtering.
     */
    val basePath: Path?

    /**
     * Paths to analyze. Works with files and directories.
     */
    val inputPaths: Collection<Path>

    /**
     * Globbing patterns to exclude sub paths of [inputPaths].
     */
    val excludes: Collection<String>

    /**
     * Globbing patterns which apply after paths get excluded by [excludes].
     */
    val includes: Collection<String>
}
