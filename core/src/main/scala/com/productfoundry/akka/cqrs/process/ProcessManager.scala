package com.productfoundry.akka.cqrs.process

import akka.actor.{ActorLogging, FSM}
import com.productfoundry.akka.cqrs.publish.EventSubscriber
import com.productfoundry.akka.cqrs.{AggregateEventRecord, Entity}
import com.productfoundry.akka.messaging.{MessageSubscriber, PersistentDeduplication}

/**
 * Process manager receives events and generates commands.
 */
trait ProcessManager[S, D]
  extends Entity
  with EventSubscriber
  with PersistentDeduplication
  with FSM[S, D]
  with ActorLogging {

  /**
   * The current event record.
   */
  private var eventRecordOption: Option[AggregateEventRecord] = None

  /**
   * Provides access to the current event record.
   *
   * @return current event record.
   * @throws ProcessManagerInternalException if no current event record is available.
   */
  def eventRecord: AggregateEventRecord = eventRecordOption.getOrElse(throw ProcessManagerInternalException("Current event record not defined"))

  /**
   * Handles an event message.
   */
  override def receiveCommand: Receive = {
    case eventRecord: AggregateEventRecord =>
      try {
        eventRecordOption = Some(eventRecord)
      } finally {
        eventRecordOption = None
      }

    case msg =>
      log.warning("Unexpected: {}", msg)
  }
}
