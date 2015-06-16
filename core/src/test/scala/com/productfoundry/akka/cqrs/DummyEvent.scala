package com.productfoundry.akka.cqrs

case class DummyEvent(id: DummyId, value: Int) extends AggregateEvent {
  override type Id = DummyId
}
