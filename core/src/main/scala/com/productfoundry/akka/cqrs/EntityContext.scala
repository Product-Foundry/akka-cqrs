package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, Props}

import scala.reflect.ClassTag

/**
  * Implements the context in which entities are created.
  */
trait EntityContext {

  def entitySupervisorFactory[E <: Entity : EntityFactory : EntityIdResolution : ClassTag]: EntitySupervisorFactory[E]

  /**
    * TODO [AK] This should not be needed, there is some logical flaw in the context design related to context
    */
  def singletonActor(props: Props, name: String): ActorRef

  /**
    * TODO [AK] This should not be needed, there is some logical flaw in the context design related to context
    */
  def localContext: EntityContext
}