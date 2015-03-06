package com.productfoundry.akka.cqrs

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.JsonMapping.TypeChoiceFormat
import com.productfoundry.akka.cqrs.TestAggregate._
import play.api.libs.json.Json

case class TestId(uuid: Uuid) extends AggregateId
object TestId extends AggregateIdIdCompanion[TestId]

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate[AggregateEvent, TestState] {

  override val factory = TestState.apply

  override def handleCommand(expected: AggregateRevision): Receive = {
    case Create(aggregateId) =>
      tryCreate(revision) {
        Right(Changes(Created(aggregateId)))
      }

    case Count(aggregateId) =>
      tryUpdate(revision) {
        Right(Changes(Counted(aggregateId, state.count + 1)))
      }

    case GetCount(_) =>
      sender() ! state.count
  }
}

object TestAggregate {
  sealed trait TestMessage extends AggregateMessage {
    override type Id = TestId
  }

  sealed trait TestAggregateCommand extends TestMessage with AggregateCommand

  case class Create(id: TestId) extends TestAggregateCommand
  case class Count(id: TestId) extends TestAggregateCommand

  sealed trait TestEvent extends TestMessage with AggregateEvent

  case class Created(id: TestId) extends TestEvent
  case class Counted(id: TestId, count: Int) extends TestEvent

  implicit val TestEventFormat: TypeChoiceFormat[TestEvent] = TypeChoiceFormat(
    "Created" -> Json.format[Created],
    "Counted" -> Json.format[Counted]
  )

  case class GetCount(id: TestId) extends TestMessage
}

object TestState extends AggregateStateFactory[AggregateEvent, TestState] {
  override def apply = {
    case Created(_) => TestState(0)
  }
}

case class TestState(count: Int) extends AggregateState[AggregateEvent, TestState] {
  override def update = {
    case Counted(_, _count) => copy(count = _count)
  }
}
