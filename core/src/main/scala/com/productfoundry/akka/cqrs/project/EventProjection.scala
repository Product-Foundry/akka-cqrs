package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{AggregateEvent, Commit}

/**
 * Defines a projection for events.
 *
 * @tparam P projection type.
 */
trait EventProjection[P <: EventProjection[P]] extends Projection[P] {

  self: P =>

  /**
   * Projects a single event container.
   */
  override def project(commit: Commit): P = {
    commit.records.zipWithIndex.foldLeft(this) { case (state, (event, index)) =>
      state.project(commit, index, event.event)
    }
  }

  /**
   * Projects a single event.
   * @param commit containing the event.
   * @param index of the event in the commit.
   * @param event to project.
   * @return Projection result.
   */
  def project(commit: Commit, index: Int, event: AggregateEvent): P
}
