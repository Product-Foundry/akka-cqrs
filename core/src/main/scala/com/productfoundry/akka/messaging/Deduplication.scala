package com.productfoundry.akka.messaging

/**
 * Prevent duplicate messages from being handled multiple times.
 */
trait Deduplication {

  private var processedDeduplicationIds: Set[String] = Set.empty

  /**
   * Indicates if a message is already processed.
   * @param deduplicationId to check.
   * @return true if the message is already processed, otherwise false.
   */
  def isAlreadyProcessed(deduplicationId: String): Boolean = processedDeduplicationIds.contains(deduplicationId)

  /**
   * Marks a message as processed.
   * @param deduplicationId to mark as processed.
   */
  def markAsProcessed(deduplicationId: String): Unit = processedDeduplicationIds += deduplicationId
}
