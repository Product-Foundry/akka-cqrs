package com.productfoundry.akka.cqrs

/**
 * Message that can be sent to an aggregate.
 */
trait AggregateMessage extends EntityMessage {

  type Id <: EntityId

  def id: Id
}