package com.productfoundry.akka.cqrs

case class DummyEvent(id: TestId, value: Int) extends AggregateEvent {
  override type Id = TestId
}
