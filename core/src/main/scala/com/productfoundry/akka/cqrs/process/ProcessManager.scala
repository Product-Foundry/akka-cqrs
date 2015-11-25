package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Process manager receives events and generates commands.
  *
  * Messages are de-duplicated to ensure they are handled only once. If a process flow is more complex and needs to
  * be resumed for example, consider mixing ProcessManager with [[akka.persistence.fsm.PersistentFSM]].
  */
trait ProcessManager
  extends Entity
  with ActorLogging
  with ReceivePipeline
  with EventPublicationInterceptor {

  private var deduplicationIds: Set[String] = Set.empty

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
    * Handles an event record.
    */
  override def receiveCommand: Receive = {
    case eventRecord: AggregateEventRecord =>

      val deduplicationId = eventRecord.tag.value
      if (deduplicationIds.contains(deduplicationId)) {
        log.debug("Skipping duplicate: {}", deduplicationId)
      } else {
        persist(DeduplicationEntry(deduplicationId)) { _ =>
          deduplicationIds = deduplicationIds + deduplicationId

          try {
            _eventRecordOption = Some(eventRecord)
            receiveEvent(eventRecord)
          } finally {
            _eventRecordOption = None
          }
        }
      }
  }

  override def receiveRecover: Receive = {
    case DeduplicationEntry(deduplicationId) =>
      deduplicationIds = deduplicationIds + deduplicationId
  }

  /**
    * Handles the received event.
    * @param eventRecord to handle.
    */
  def receiveEvent(eventRecord: AggregateEventRecord): Unit
}
