package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.{EventPublication, EventSubscriber}
import com.productfoundry.akka.messaging.{Deduplication, DeduplicationEntry}

/**
 * Process manager receives events and generates commands.
 *
 * Messages are de-duplicated to ensure they are handled only once. If a process flow is more complex and needs to
 * be resumed for example, consider mixing ProcessManager with [[akka.persistence.fsm.PersistentFSM]].
 */
trait ProcessManager
  extends Entity
  with EventSubscriber
  with Deduplication
  with ActorLogging {

  /**
   * The current event record.
   */
  private var _eventRecordOption: Option[AggregateEventRecord] = None

  /**
   * @return Indication if there is an event record available.
   */
  def hasEventRecord: Boolean = _eventRecordOption.isDefined

  /**
   * Provides access to the current event record if it is available.
   *
   * @return current event record option.
   */
  def eventRecordOption: Option[AggregateEventRecord] = _eventRecordOption

  /**
   * Provides access to the current event record.
   *
   * @return current event record.
   * @throws ProcessManagerInternalException if no current event record is available.
   */
  def eventRecord: AggregateEventRecord = _eventRecordOption.getOrElse(throw ProcessManagerInternalException("Current event record not defined"))

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
        _eventRecordOption = Some(eventRecord)
        receiveEvent(eventRecord.tag, eventRecord.headers).applyOrElse(eventRecord.event, unhandled)
      } finally {
        _eventRecordOption = None
      }
  }

  type ReceiveEvent = PartialFunction[AggregateEvent, Unit]

  def receiveEvent(tag: AggregateTag, headers: AggregateEventHeaders): ReceiveEvent

  // TODO [AK] Correlation
}
