package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

trait EntitySystem {
  def entitySupervisorFactory[E <: Entity : EntityFactory : ClassTag]: EntitySupervisorFactory[E]
}