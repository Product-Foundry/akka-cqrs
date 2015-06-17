package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import com.productfoundry.akka.cqrs.publish.{EventPublication, EventSubscriber}
import com.productfoundry.akka.cqrs.{AggregateEventRecord, Entity}
import com.productfoundry.akka.messaging.Deduplication

/**
 * Process manager receives events and generates commands.
 *
 * Messages are deduplicated to ensure they are handled only once.
 */
trait ProcessManager[S, D]
  extends Entity
  with EventSubscriber
  with Deduplication
  with ActorLogging {

  /**
   * Receive function for aggregate events
   */
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
    case publication: EventPublication =>
      publication.confirmIfRequested()
      processDeduplicatable(publication)(duplicate)(unique)
      eventReceived.applyOrElse(publication.eventRecord, unhandled)
  }

  /**
   * Handler for duplicate publications.
   * @param publication already processed.
   */
  private def duplicate(publication: EventPublication): Unit = {
    log.debug("Skipping duplicate: {}", eventRecord)
  }

  /**
   * Handler for unique publications.
   * @param publication to process.
   */
  private def unique(publication: EventPublication): Unit = {
    persist(Deduplication.Received(publication.deduplicationId)) { _ =>
      markAsProcessed(publication.deduplicationId)
    }
  }

  override def receiveRecover: Receive = {
    case Deduplication.Received(deduplicationId) =>
      markAsProcessed(deduplicationId)
  }

  /**
   * Partial function to handle published aggregate event records.
   */
  override def eventReceived: ReceiveEventRecord = {
    case eventRecord: AggregateEventRecord =>
      try {
        eventRecordOption = Some(eventRecord)
        receiveEventRecord(eventRecord)
      } finally {
        eventRecordOption = None
      }
  }

  def receiveEventRecord: ReceiveEventRecord
}
