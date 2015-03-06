package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

/**
 * All aggregates have identity.
 */
trait AggregateId extends EntityId

abstract class AggregateIdCompanion[I <: AggregateId : ClassTag] extends EntityIdCompanion[I]