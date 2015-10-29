package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.{EventPublication, EventSubscriber}
import com.productfoundry.akka.messaging.{Deduplication, DeduplicationEntry}

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
  }

  /**
   * Handler for duplicate publications.
   * @param publication already processed.
   */
  private def duplicate(publication: EventPublication): Unit = {
    log.debug("Skipping duplicate: {}", publication)
  }

  /**
   * Handler for unique publications.
   * @param publication to process.
   */
  private def unique(publication: EventPublication): Unit = {
    persist(DeduplicationEntry(publication.deduplicationId)) { _ =>
      markAsProcessed(publication.deduplicationId)
      eventReceived.applyOrElse(publication.eventRecord, unhandled)
    }
  }

  override def receiveRecover: Receive = {
    case DeduplicationEntry(deduplicationId) =>
      markAsProcessed(deduplicationId)
  }

  /**
   * Partial function to handle published aggregate event records.
   */
  override def eventReceived: ReceiveEventRecord = {
    case eventRecord: AggregateEventRecord =>
      try {
        eventRecordOption = Some(eventRecord)
        receiveEvent(eventRecord.tag, eventRecord.headers).applyOrElse(eventRecord.event, unhandled)
      } finally {
        eventRecordOption = None
      }
  }

  type ReceiveEvent = PartialFunction[AggregateEvent, Unit]

  def receiveEvent(tag: AggregateTag, headers: AggregateEventHeaders): ReceiveEvent

  // TODO [AK] Correlation
}
