package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.AggregateEventRecord

class DummyProcessManager(val passivationConfig: PassivationConfig) extends ProcessManager[Any, Any] {

  override def receiveEventRecord: ReceiveEventRecord = {
    case AggregateEventRecord(_, _, event) =>
      context.system.eventStream.publish(event)
  }
}
