package com.productfoundry.akka.cqrs

import akka.actor._

/**
 * Entity supervisor creation using an implicit entity supervisor factory.
 *
 * Usage: val entity = EntitySupervisor[Entity]
 */
object EntitySupervisor {

  /**
   * Creates entities based on entity type.
   */
  def forType[E <: Entity : EntityFactory : EntitySupervisorFactory]: ActorRef = {
    implicitly[EntitySupervisorFactory[E]].getOrCreate
  }
}

