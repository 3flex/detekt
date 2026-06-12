package dev.detekt.gradle.extensions

import dev.detekt.gradle.extensions.DetektReportType.CHECKSTYLE
import dev.detekt.gradle.extensions.DetektReportType.HTML
import dev.detekt.gradle.extensions.DetektReportType.MARKDOWN
import dev.detekt.gradle.extensions.DetektReportType.SARIF
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import javax.inject.Inject

/**
 * Container for the reports that a [dev.detekt.gradle.Detekt] task can produce.
 *
 * It exposes one [DetektReport] per built-in format and supports an arbitrary number of [custom] reports.
 */
@Suppress("TooManyFunctions")
open class DetektReports @Inject constructor(@get:Internal val objects: ObjectFactory) {

    /** The Checkstyle XML report. */
    @get:Nested
    open val checkstyle: DetektReport = objects.newInstance(DetektReport::class.java, CHECKSTYLE)

    /** The HTML report. */
    @get:Nested
    open val html: DetektReport = objects.newInstance(DetektReport::class.java, HTML)

    /** The SARIF report. */
    @get:Nested
    open val sarif: DetektReport = objects.newInstance(DetektReport::class.java, SARIF)

    /** The Markdown report. */
    @get:Nested
    open val markdown: DetektReport = objects.newInstance(DetektReport::class.java, MARKDOWN)

    /** The custom reports contributed by detekt rule set plugins. */
    @get:Nested
    open val custom = mutableListOf<CustomDetektReport>()

    /**
     * Configures the [checkstyle] report.
     *
     * @param action action used to enable or customize the report
     */
    fun checkstyle(action: Action<in DetektReport>): Unit = action.execute(checkstyle)

    /**
     * Configures the [html] report.
     *
     * @param action action used to enable or customize the report
     */
    fun html(action: Action<in DetektReport>): Unit = action.execute(html)

    /**
     * Configures the [sarif] report.
     *
     * @param action action used to enable or customize the report
     */
    fun sarif(action: Action<in DetektReport>): Unit = action.execute(sarif)

    /**
     * Configures the [markdown] report.
     *
     * @param action action used to enable or customize the report
     */
    fun markdown(action: Action<in DetektReport>): Unit = action.execute(markdown)

    /**
     * Registers and configures a new [custom] report.
     *
     * @param action action used to set the report id and output location
     */
    fun custom(action: Action<in CustomDetektReport>): Unit = action.execute(createAndAddCustomReport())

    private fun createAndAddCustomReport() =
        objects.newInstance(CustomDetektReport::class.java).apply { custom.add(this) }
}
