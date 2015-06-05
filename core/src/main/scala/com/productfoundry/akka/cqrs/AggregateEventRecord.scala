package com.productfoundry.akka.cqrs

/**
 * Persisted aggregate event.
 * @param revision of the aggregate after the event will be applied.
 * @param event with the actual change.
 */
case class AggregateEventRecord(revision: AggregateRevision, event: AggregateEvent)
