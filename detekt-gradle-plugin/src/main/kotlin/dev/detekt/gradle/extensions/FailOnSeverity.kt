package dev.detekt.gradle.extensions

/**
 * The minimum issue severity that causes a detekt task to fail the build.
 *
 * A task fails when it reports at least one issue whose severity is equal to or higher than the
 * configured value. The severities are ordered from highest to lowest: [Error], [Warning], [Info].
 */
enum class FailOnSeverity {
    /** Fail only when an issue with `Error` severity is reported. */
    Error,

    /** Fail when an issue with `Warning` or `Error` severity is reported. */
    Warning,

    /** Fail when any issue, regardless of severity, is reported. */
    Info,

    /** Never fail the build, regardless of the issues reported. */
    Never,
}
