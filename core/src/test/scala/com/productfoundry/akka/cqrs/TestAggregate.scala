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
      tryUpdate {
        Right(Changes(Counted(aggregateId, state.count + 1)))
      }

    case CountWithPayload(aggregateId) =>
      tryUpdate {
        Right(Changes(Counted(aggregateId, state.count + 1)).withPayload(state.count))
      }

    case Increment(aggregateId, amount) =>
      tryUpdate {
        if (amount > 0) {
          Right(Changes(Incremented(aggregateId, amount)))
        } else {
          Left(ValidationError(InvalidIncrement(amount)))
        }
      }

    case Delete(aggregateId) =>
      tryUpdate {
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
      case Incremented(_, amount) => copy(count = count + amount)
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
  case class CountWithPayload(id: TestId) extends TestAggregateCommand
  case class Increment(id: TestId, amount: Int) extends TestAggregateCommand
  case class Delete(id: TestId) extends TestAggregateCommand

  sealed trait TestEvent extends TestMessage with AggregateEvent

  case class Created(id: TestId) extends TestEvent
  case class Counted(id: TestId, count: Int) extends TestEvent
  case class Incremented(id: TestId, amount: Int) extends TestEvent
  case class Deleted(id: TestId) extends TestEvent with AggregateDeleteEvent

  sealed trait TestValidationMessage extends ValidationMessage

  case class InvalidIncrement(value: Int) extends TestValidationMessage

  case class GetCount(id: TestId) extends TestMessage
}
