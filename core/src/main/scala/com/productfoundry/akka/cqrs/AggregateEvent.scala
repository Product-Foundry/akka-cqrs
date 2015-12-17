package com.productfoundry.akka.cqrs

/**
  * Base event marker trait.
  *
  * Users needs to and ensure there is a proper Akka serializer configured for their events.
  */
trait AggregateEvent extends AggregateMessage

/**
  * Marker trait to indicate this event deletes the aggregate state.
  */
trait AggregateDeleteEvent {
  self: AggregateEvent =>
}


