package com.productfoundry.akka.cqrs

/**
 * Marker trait to indicate this event deletes the aggregate state.
 */
trait AggregateDeleteEvent extends AggregateEvent
