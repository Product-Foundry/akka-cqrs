package com.productfoundry.akka.cqrs

import akka.persistence.PersistentActor
import com.productfoundry.akka.GracefulPassivation

object Entity {

  type EntityId = String
}

/**
 * Defines a domain entity.
 */
trait Entity extends PersistentActor with GracefulPassivation {

  final val entityName = context.parent.path.name

  final val entityId = self.path.name

  final val _persistenceId = s"$entityName/$entityId"

  override def persistenceId: String = _persistenceId

  if (persistenceId != _persistenceId) {
    throw new AssertionError(s"Persistence id is invalid, is it changed by a trait? Expected: $entityId, actual: $persistenceId")
  }
}