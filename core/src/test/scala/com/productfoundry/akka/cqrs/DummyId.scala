package com.productfoundry.akka.cqrs

import java.util.UUID

case class DummyId(entityId: String) extends EntityId

object DummyId {

  def generate(): DummyId = DummyId(UUID.randomUUID().toString)
}