package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver

class AggregateIdResolution[A] extends EntityIdResolution[A] {

  override def entityIdResolver: EntityIdResolver = {
    case msg: AggregateMessage => msg.id.toString
  }
}
