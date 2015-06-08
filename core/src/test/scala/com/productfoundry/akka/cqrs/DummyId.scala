package com.productfoundry.akka.cqrs

case class TestId(uuid: Uuid) extends AggregateId

object TestId extends AggregateIdCompanion[TestId]
