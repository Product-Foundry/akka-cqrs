package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.project.domain.DomainProjection
import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateEventRecord}

case class EventCollector(events: Vector[AggregateEvent] = Vector.empty) extends DomainProjection[EventCollector] {

  /**
   * Projects a single event record.
   */
  override def project(revision: ProjectionRevision, eventRecord: AggregateEventRecord): EventCollector = {
    copy(events = events :+ eventRecord.event)
  }
}
