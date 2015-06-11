package com.productfoundry.akka.messaging

import com.productfoundry.akka.cqrs.Entity.EntityId

/**
 * Prevent duplicate messages from being handled multiple times.
 */
trait Deduplication {

  private var processedMessageIds: Set[String] = Set.empty

  /**
   * Indicates if a message is already processed.
   * @param messageId to check.
   * @return true if the message is already processed, otherwise false.
   */
  def isAlreadyProcessed(messageId: String): Boolean = processedMessageIds.contains(messageId)

  /**
   * Marks a message as processed.
   * @param messageId to mark as processed.
   */
  def markAsProcessed(messageId: EntityId): Unit = processedMessageIds += messageId
}
