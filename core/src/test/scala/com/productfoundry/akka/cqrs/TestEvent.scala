package com.productfoundry.akka.cqrs

case class TestEvent(id: TestId, value: Int) extends AggregateEvent {
  override type Id = TestId
}
