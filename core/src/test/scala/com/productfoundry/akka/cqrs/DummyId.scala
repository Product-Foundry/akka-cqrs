package com.productfoundry.akka.cqrs

case class DummyId(uuid: Uuid) extends AggregateId

object DummyId extends AggregateIdCompanion[DummyId]
