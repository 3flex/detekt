package io.gitlab.arturbosch.detekt.cli

import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.Findings
import io.gitlab.arturbosch.detekt.cli.baseline.BaselineFacade

class FilteredDetectionResult(detektion: Detektion, baselineFacade: BaselineFacade) : Detektion by detektion {

    private val filteredFindings: Findings

    init {
        filteredFindings = detektion.findings
                .map { (key, value) -> key to baselineFacade.filter(value) }
                .toMap()
    }

    override val findings = filteredFindings
}
