package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

import scala.reflect.ClassTag

/**
  * Consistently provide access to aggregates through supervisor actors.
  *
  * Typically sets up an entity context and forwards all actor requests to [[EntityContext.entitySupervisorFactory]]
  */
trait AggregateRegistry {

  /**
    * @tparam A aggregate type.
    * @return the aggregate's supervisor ref.
    */
  def apply[A <: Aggregate : ClassTag]: ActorRef
}
