package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.{AggregateEventHeaders, AggregateTag}

class DummyProcessManager(val passivationConfig: PassivationConfig) extends ProcessManager[Any, Any] {

  override def receiveEvent(tag: AggregateTag, headers: AggregateEventHeaders): ReceiveEvent = {
    case event => context.system.eventStream.publish(event)
  }
}
