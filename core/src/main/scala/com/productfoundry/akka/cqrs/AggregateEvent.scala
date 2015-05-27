package com.productfoundry.akka.cqrs

/**
 * Base event marker trait.
 */
trait AggregateEvent extends AggregateMessage with DomainEvent {

  /**
   * @return Indication if this event should mark the aggregate as deleted.
   */
  def isDeleteEvent: Boolean = false
}

/**
 * Marker trait to indicate this event deletes the aggregate state.
 */
trait AggregateDeleteEvent {
  self: AggregateEvent =>

  /**
   * Marks the aggregate as deleted.
   */
  override def isDeleteEvent: Boolean = true
}


