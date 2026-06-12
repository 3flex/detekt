package dev.detekt.gradle.extensions

/**
 * The built-in report formats that a [dev.detekt.gradle.Detekt] task can produce.
 *
 * @property reportId the identifier used for this report on the detekt CLI
 * @property extension the file extension used for this report's output file
 */
enum class DetektReportType(val reportId: String, val extension: String) {

    /** The Checkstyle XML report. */
    CHECKSTYLE("checkstyle", "xml"),

    /** The HTML report. */
    HTML("html", "html"),

    /** The SARIF report. */
    SARIF("sarif", "sarif"),

    /** The Markdown report. */
    MARKDOWN("markdown", "md"),
    ;

    internal companion object {
        fun isWellKnownReportId(reportId: String) = reportId in values().map(DetektReportType::reportId)
    }
}
