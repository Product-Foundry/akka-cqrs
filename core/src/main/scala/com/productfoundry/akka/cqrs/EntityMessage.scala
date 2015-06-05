package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.Entity.EntityId

/**
 * Message that can be sent to a root entity.
 */
trait EntityMessage {

  /**
   * Entity id is required to properly route entity messages.
   */
  def entityId: EntityId
}