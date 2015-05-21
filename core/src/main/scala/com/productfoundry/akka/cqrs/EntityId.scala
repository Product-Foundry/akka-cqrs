package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

/**
 * All entities have identity.
 */
trait EntityId extends Identifier

abstract class EntityIdCompanion[I <: EntityId : ClassTag] extends IdentifierCompanion[I]