package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag

/**
 * All aggregates have identity.
 */
trait AggregateId extends Identifier

abstract class AggregateIdCompanion[I <: AggregateId : ClassTag] extends IdentifierCompanion[I] {

  implicit val AggregateIdCompanionObject: AggregateIdCompanion[I] = this
}
