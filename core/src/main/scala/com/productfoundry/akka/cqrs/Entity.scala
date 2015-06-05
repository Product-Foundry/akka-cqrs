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

  final val entityId = s"${context.parent.path.name}/${self.path.name}"

  override def persistenceId: String = entityId

  if (entityId != persistenceId) {
    throw new AssertionError(s"Persistence id is invalid, is it changed by a trait? Expected: $entityId, actual: $persistenceId")
  }
}