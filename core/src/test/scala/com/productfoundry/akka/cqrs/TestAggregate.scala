package com.productfoundry.akka.cqrs

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TestAggregate._

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate[TestEvent] {

  type S = TestState

  override def handleCommand: Receive = {
    case Create(aggregateId) =>
      tryCreate {
        Right(Changes(Created(aggregateId)))
      }

    case Count(aggregateId) =>
      tryUpdate(revision) {
        Right(Changes(Counted(aggregateId, state.count + 1)))
      }

    case Delete(aggregateId) =>
      tryUpdate(revision) {
        Right(Changes(Deleted(aggregateId)))
      }

    case GetCount(_) =>
      sender() ! state.count
  }

  override val factory: StateFactory = {
    case Created(_) => TestState(0)
  }

  case class TestState(count: Int) extends AggregateState {
    override def update = {
      case Counted(_, _count) => copy(count = _count)
    }
  }
}

object TestAggregate {
  sealed trait TestMessage extends AggregateMessage {
    override type Id = TestId
  }

  sealed trait TestAggregateCommand extends TestMessage with AggregateCommand

  case class Create(id: TestId) extends TestAggregateCommand
  case class Count(id: TestId) extends TestAggregateCommand
  case class Delete(id: TestId) extends TestAggregateCommand

  sealed trait TestEvent extends TestMessage with AggregateEvent

  case class Created(id: TestId) extends TestEvent
  case class Counted(id: TestId, count: Int) extends TestEvent
  case class Deleted(id: TestId) extends TestEvent with AggregateDeleteEvent

  case class GetCount(id: TestId) extends TestMessage
}
