package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

/**
  * Implements the context in which entities are created.
  */
trait EntityContext {
  def entitySupervisorFactory[E <: Entity : EntityFactory : EntityIdResolution : ClassTag]: EntitySupervisorFactory[E]
}