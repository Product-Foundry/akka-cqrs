package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.AggregateEventContainer

/**
 * Defines a projection.
 *
 * @tparam C type of container with events
 * @tparam R projection result type
 */
private[project] trait ContainerProjection[C <: AggregateEventContainer, R] {

  /**
   * Projects a single event container.
   */
  def project(eventContainer: C): R
}
