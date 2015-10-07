package com.productfoundry.akka.cqrs

/**
 * Message that can be sent to an aggregate.
 */
trait AggregateMessage extends EntityMessage {

  type Id <: EntityId

  /**
   * Aggregate identity has more specific requirements.
   */
  def id: Id
}