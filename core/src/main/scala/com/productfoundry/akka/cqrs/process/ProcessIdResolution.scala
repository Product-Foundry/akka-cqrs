package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.{EntityId, AggregateEventRecord, AggregateEvent, EntityIdResolution}
import com.productfoundry.akka.cqrs.EntityIdResolution._
import com.productfoundry.akka.cqrs.publish.EventPublication

/**
 * Process id resolution only makes sense for the actual event data.
 *
 * Since there is some complexity around publication, this trait provides a convenience entity id resolver that
 * simplifies this process.
 */
trait ProcessIdResolution[A] extends EntityIdResolution[A] {

  type ProcessIdResolver = PartialFunction[AggregateEvent, EntityId]

  def processIdResolver: ProcessIdResolver

  override def entityIdResolver: EntityIdResolver = {
    case eventPublication: EventPublication if processIdResolver.isDefinedAt(eventPublication.eventRecord.event) =>
      processIdResolver(eventPublication.eventRecord.event)

    case eventRecord: AggregateEventRecord if processIdResolver.isDefinedAt(eventRecord.event) =>
      processIdResolver(eventRecord.event)

    case event: AggregateEvent if processIdResolver.isDefinedAt(event) =>
      processIdResolver(event)
  }
}
