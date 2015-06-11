package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.Entity.EntityId

/**
 * Message that can be sent to an aggregate.
 */
trait AggregateMessage extends EntityMessage {

  type Id <: AggregateId

  /**
   * Aggregate identity has more specific requirements.
   */
  def id: Id

  /**
   * Entity id is used by default to route entity messages.
   */
  final def entityId: EntityId = id.toString
}