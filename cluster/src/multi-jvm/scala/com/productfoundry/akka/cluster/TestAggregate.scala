package com.productfoundry.akka.cluster

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs._
import com.sun.corba.se.impl.activation.CommandHandler

case class TestId(entityId: String) extends EntityId

trait TestMessage extends AggregateMessage {
  override type Id = TestId
}

trait TestCommand extends TestMessage with AggregateCommand

case class Count(id: TestId) extends TestCommand

trait TestEvent extends TestMessage with AggregateEvent

case class Counted(id: TestId, value: Int) extends TestEvent

case class GetCount(id: TestId) extends TestMessage

case class GetCountResult(count: Int)

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate {

  override type S = TestState

  override type M = TestMessage

  override val messageClass = classOf[M]

  case class TestState(counter: Int = 0) extends AggregateState {

    override def update: StateModifications = {
      case Counted(id, value) => copy(counter = counter + 1)
    }
  }

  override val factory: StateModifications = {
    case Counted(_, value) => TestState(counter = value)
  }

  override def handleCommand: CommandHandler = {
    case Count(id) =>
      Right(Changes(Counted(id, stateOption.fold(1)(_.counter + 1))))
  }

  override def unhandled(message: Any): Unit = message match {
    case GetCount(id) =>
      sender() ! GetCountResult(stateOption.fold(0)(_.counter))
    case _ =>
      super.unhandled(message)
  }
}
