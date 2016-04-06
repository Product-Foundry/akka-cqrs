package com.productfoundry.akka.cqrs

import com.productfoundry.akka.serialization.Persistable

/**
  * Event that is persisted and applied to an aggregate.
  *
  * Aggregate event records are perfect to expose outside the core system, since they contain the event with
  * all commit related data.
  *
  * @param tag           of the aggregate after the event was applied.
  * @param headersOption with optional info about the aggregate related to the event.
  * @param event         with the actual change.
  */
case class AggregateEventRecord(tag: AggregateTag, headersOption: Option[CommitHeaders], event: AggregateEvent)
  extends EntityMessage
    with Persistable {

  def hasHeaders: Boolean = headersOption.isDefined

  def headers: CommitHeaders = headersOption.getOrElse(throw new IllegalArgumentException("Event record " + tag.value + " has no headers"))
}