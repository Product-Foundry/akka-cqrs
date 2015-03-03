package com.productfoundry.akka.cqrs

/**
 * Message that can be sent to a root entity.
 */
trait EntityMessage {

  /**
   * @return The id of the root entity.
   */
  def entityId: EntityId
}
