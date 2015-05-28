package com.productfoundry.akka.cqrs

/**
 * Defines persistable container with events.
 */
trait AggregateEventContainer {

  /**
   * @return unique id of the container.
   */
  def id: String

  /**
   * @return events.
   */
  def events: Seq[AggregateEvent]
}
