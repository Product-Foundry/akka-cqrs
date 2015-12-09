package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.{EntityId, EntityMessage}

/**
  * Process manager event used for persisting process state
  */
trait ProcessManagerEvent extends EntityMessage {

  type Id <: EntityId

  def id: Id
}