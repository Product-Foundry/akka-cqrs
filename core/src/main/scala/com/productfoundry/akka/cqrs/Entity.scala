package com.productfoundry.akka.cqrs

/**
 * Defines a domain entity.
 */
trait Entity {

  /**
   * All domain entities have an id.
   * @return The id of the domain entity.
   */
  def id: EntityId
}
