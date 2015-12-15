package com.productfoundry.akka.cluster

import java.util.UUID

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs._

case class TestId(entityId: String) extends EntityId

trait TestMessage extends AggregateMessage {
  override type Id = TestId
}

trait TestCommand extends TestMessage with AggregateCommand

case class Count(id: TestId) extends TestCommand

trait TestEvent extends TestMessage with AggregateEvent

case class Counted(id: TestId, value: Int) extends TestEvent

object TestId {

  def generate(): TestId = TestId(UUID.randomUUID().toString)
}

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate {

  type S = TestState

  case class TestState(counter: Int = 0) extends AggregateState {

    override def update: StateModifications = {
      case Counted(id, value) => copy(counter = counter + 1)
    }
  }

  override val factory: StateModifications = {
    case _ => TestState()
  }

  override def handleCommand: Receive = {
    case Count(id) => tryCommit(Right(Changes(Counted(id, state.counter + 1))))
  }
}
