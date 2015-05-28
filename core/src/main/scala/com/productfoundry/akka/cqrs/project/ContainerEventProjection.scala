package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateEventContainer}

/**
 * Defines a projection for events.
 *
 * @tparam C type of container with events
 * @tparam P projection type.
 */
private[project] trait ContainerEventProjection[C <: AggregateEventContainer, P <: ContainerEventProjection[C, P]] extends ContainerProjection[C, P] {

  self: P =>

  /**
   * Projects a single event container.
   */
  override def project(container: C): P = {
    container.events.zipWithIndex.foldLeft(this) { case (state, (event, eventIndex)) =>
      state.project(container, event)
    }
  }

  /**
   * Projects a single event.
   * @param container containing the event.
   * @param event to project.
   * @return Projection result.
   */
  def project(container: C, event: AggregateEvent): P
}
