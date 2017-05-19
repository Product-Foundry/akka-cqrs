package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

import scala.reflect.ClassTag

/**
 * Abstract entity supervisor factory.
 */
abstract class EntitySupervisorFactory[E <: Entity : EntityFactory : ClassTag] {

  /**
   * Gets or creates an entity supervisor for the specified type.
   * @return Created supervisor.
   */
  def getOrCreate: ActorRef

  /**
   * The supervisor name is based on the entity type and can be used in the actor name.
   * @return Supervisor name.
   */
  def supervisorName: String = implicitly[ClassTag[E]].runtimeClass.getSimpleName
}