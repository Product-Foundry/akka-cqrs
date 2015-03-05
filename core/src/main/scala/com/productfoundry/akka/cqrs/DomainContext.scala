package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

trait DomainContext {
  def entitySupervisorFactory[E <: Entity : EntityFactory : ClassTag]: EntitySupervisorFactory[E]
}