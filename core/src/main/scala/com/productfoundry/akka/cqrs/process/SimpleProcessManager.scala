package com.productfoundry.akka.cqrs.process

import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Simple process manager that de-duplicates message to ensure that they are handled only once.
  *
  * If a message cannot be handled processing that message will not be attempted again.
  *
  * If a process flow is more complex and needs to be resumed for example, consider
  * extending [[ProcessManager]] with [[akka.persistence.fsm.PersistentFSM]].
  */
trait SimpleProcessManager
  extends ProcessManager
  with ReceivePipeline
  with EventPublicationInterceptor{

  private var deduplicationIds: Set[String] = Set.empty

  /**
    * The current event record.
    */
  private var _eventRecordOption: Option[AggregateEventRecord] = None

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
