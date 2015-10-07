package com.productfoundry.akka.cqrs

/**
 * All entities have an ID, which is used by the supervisor to route messages.
 */
trait EntityId {

  /**
   * @return String representation of the id of the entity.
   */
  def entityId: String
}
