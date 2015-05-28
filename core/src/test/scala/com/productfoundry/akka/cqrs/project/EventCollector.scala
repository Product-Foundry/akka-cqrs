package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{AggregateEvent, Commit}

case class EventCollector(events: Vector[AggregateEvent] = Vector.empty) extends EventProjection[EventCollector] {
  override def project(commit: Commit, index: Int, event: AggregateEvent): EventCollector = {
    copy(events = events :+ event)
  }
}
