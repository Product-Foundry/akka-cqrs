package com.productfoundry.akka.cqrs

/**
 * Event that is persisted and applied to an aggregate.
 *
 * Aggregate event records are perfect to expose outside the core system, since they contain the event with
 * all commit related data.
 *
 * @param tag of the aggregate after the event was applied.
 * @param headers with info about the aggregate related to the event.
 * @param event with the actual change.
 */
case class AggregateEventRecord(tag: AggregateTag, headers: AggregateEventHeaders, event: AggregateEvent) extends EntityMessage