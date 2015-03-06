package com.productfoundry.akka.cqrs

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TestAggregate._

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate[DomainEvent, TestState] {

  override val factory = TestState.apply

  override def handleCommand(expected: AggregateRevision): Receive = {
    case Create(entityId) =>
      tryCreate(revision) {
        Right(Changes(Created(entityId)))
      }

    case Count(entityId) =>
      tryUpdate(revision) {
        Right(Changes(Counted(entityId, state.count + 1)))
      }

    case GetCount(_) =>
      sender() ! state.count
  }
}

object TestAggregate {
  case class Create(entityId: EntityId) extends Command
  case class Count(entityId: EntityId) extends Command

  case class Created(entityId: EntityId) extends DomainEvent
  case class Counted(entityId: EntityId, count: Int) extends DomainEvent

  case class GetCount(entityId: EntityId) extends EntityMessage
}

object TestState extends AggregateStateFactory[DomainEvent, TestState] {
  override def apply = {
    case Created(_) => TestState(0)
  }
}

case class TestState(count: Int) extends AggregateState[DomainEvent, TestState] {
  override def update = {
    case Counted(_, _count) => copy(count = _count)
  }
}
