package com.productfoundry.akka.messaging

import akka.actor.{Actor, ActorLogging}

/**
 * Prevent duplicate messages from being handled multiple times.
 *
 * It is the responsibility of the child class to filter out duplicates.
 * This can be done by checking [[DeduplicationHandler.duplicateMessageReceived]] or using
 * the partial function [[DeduplicationHandler.receiveDuplicate]] in your receive function.
 *
 * When non-deduplicatable messages are received, they will never be marked as duplicate.
 */

// TODO [AK] For some reason this only works when mixed on with PersistentDeduplicationHandler
trait DeduplicationHandler extends Actor with ActorLogging {

  /**
   * Track processed ids in memory.
   */
  private var processedDeduplicationIds: Set[String] = Set.empty

  /**
   * Allows checking if the current message is a duplicate or not.
   */
  private var duplicateMessageReceivedOption: Option[Boolean] = None

  /**
   * Checks if the current message is a duplicate or not.
   */
  def duplicateMessageReceived: Boolean = duplicateMessageReceivedOption.get

  /**
   * Check for duplicate messages, allows tracking of duplicate received.
   */
  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    try {
      msg match {
        case d: Deduplicatable =>
          duplicateMessageReceivedOption = Some(isAlreadyProcessed(d.deduplicationId))
          if (!duplicateMessageReceived) {
            uniqueMessageReceived(d.deduplicationId)
          }
        case _ =>
          duplicateMessageReceivedOption = Some(false)
      }

      // Always invoke around receive, since there may be other useful behavior like sending confirmations.
      super.aroundReceive(receive, msg)
    } finally {
      duplicateMessageReceivedOption = None
    }
  }

  /**
   * Partial function handles only duplicate messages.
   */
  def receiveDuplicate: Receive = {
    case d if duplicateMessageReceived => log.debug("Skipping duplicate: {}", d)
  }

  /**
   * Function to handle unique messages.
   *
   * Simply marks the message as processed by default.
   */
  def uniqueMessageReceived(deduplicationId: String): Unit = {
    markAsProcessed(deduplicationId)
  }

  /**
   * Marks the id as processed.
   *
   * Following messages with the same id will be considered duplicates.
   *
   * @param deduplicationId used to detect duplicates.
   */
  def markAsProcessed(deduplicationId: String): Unit = {
    processedDeduplicationIds = processedDeduplicationIds + deduplicationId
  }

  /**
   * Check if the specified deduplication id is already marked as processed.
   * @param deduplicationId to check.
   */
  def isAlreadyProcessed(deduplicationId: String): Boolean = {
    processedDeduplicationIds.contains(deduplicationId)
  }
}
