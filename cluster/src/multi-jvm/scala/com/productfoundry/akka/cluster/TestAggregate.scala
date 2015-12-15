package com.productfoundry.akka.cluster

import java.util.UUID

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs._

case class TestId(entityId: String) extends EntityId

trait TestMessage extends AggregateMessage {
  override type Id = TestId
}

trait TestCommand extends TestMessage with AggregateCommand

trait TestEvent extends TestMessage with AggregateEvent

object TestId {

  def generate(): TestId = TestId(UUID.randomUUID().toString)
}

class TestAggregate(val passivationConfig: PassivationConfig) extends Aggregate {

  type S = TestState

  case class TestState() extends AggregateState {

    override def update: StateModifications = {
      case _ => this
    }
  }

  override val factory: StateModifications = {
    case _ => TestState()
  }

  override def handleCommand: Receive = {
    case _ =>
  }
}
