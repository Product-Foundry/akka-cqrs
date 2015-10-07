package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver

/**
 * Aggregate ids are always resolved based on identity.
 */
class AggregateIdResolution[A] extends EntityIdResolution[A] {

  override def entityIdResolver: EntityIdResolver = {
    case msg: AggregateMessage => msg.id
  }
}