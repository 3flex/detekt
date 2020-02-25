package io.gitlab.arturbosch.detekt.api

import org.jetbrains.kotlin.com.intellij.openapi.util.Key

/**
 * Storage for all kinds of findings and additional information
 * which needs to be transferred from the detekt engine to the user.
 */
interface Detektion {
    val findings: FindingTable<RuleSetId>
    val notifications: Collection<Notification>
    val metrics: Collection<ProjectMetric>

    /**
     * Retrieves a value stored by the given key of the result.
     */
    fun <V> getData(key: Key<V>): V?

    /**
     * Stores an arbitrary value inside the result binded to the given key.
     */
    fun <V> addData(key: Key<V>, value: V)

    /**
     * Stores a notification in the result.
     */
    fun add(notification: Notification)

    /**
     * Stores a metric calculated for the whole project in the result.
     */
    fun add(projectMetric: ProjectMetric)
}

typealias Findings = Map<RuleSetId, List<Finding>>
typealias FindingTable<T> = Map<T, List<Finding>>
