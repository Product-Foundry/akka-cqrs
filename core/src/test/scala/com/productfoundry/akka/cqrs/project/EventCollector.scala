package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateEventRecord}

case class EventCollector(events: Vector[AggregateEvent] = Vector.empty) extends DirectProjection[EventCollector] {

  /**
   * Projects a single event record.
   */
  override def project(eventRecord: AggregateEventRecord): EventCollector = {
    copy(events = events :+ eventRecord.event)
  }
}
