package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.process.DummyProcessManager.LogEvent
import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateEventRecord}

class DummyProcessManager(val passivationConfig: PassivationConfig) extends ProcessManager[Any, Any] {

  override def receiveEventRecord: ReceiveEventRecord = {
    case AggregateEventRecord(_, _, event) => sender() ! LogEvent(event)
  }
}

object DummyProcessManager {

  case class LogEvent(event: AggregateEvent)
}