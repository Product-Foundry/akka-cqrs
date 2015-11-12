package com.productfoundry.akka.cqrs.process

import akka.actor.Props
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateEventHeaders, AggregateTag, EntityIdResolution}

object DummyProcessManager extends ProcessManagerCompanion[DummyProcessManager] {

  override def idResolution: EntityIdResolution[DummyProcessManager] = new ProcessIdResolution[DummyProcessManager] {
    override def processIdResolver: ProcessIdResolver = {
      case event: AggregateEvent => event.id
    }
  }

  def factory() = new ProcessManagerFactory[DummyProcessManager] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyProcessManager(config))
    }
  }
}

class DummyProcessManager(val passivationConfig: PassivationConfig) extends ProcessManager {

  override def receiveEvent(tag: AggregateTag, headers: AggregateEventHeaders): ReceiveEvent = {
    case event => context.system.eventStream.publish(event)
  }
}
