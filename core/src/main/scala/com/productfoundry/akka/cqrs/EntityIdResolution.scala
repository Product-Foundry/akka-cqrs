package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver

object EntityIdResolution {

  type EntityIdResolver = PartialFunction[Any, EntityId]
}

trait EntityIdResolution[A] {

  def entityIdResolver: EntityIdResolver
}
