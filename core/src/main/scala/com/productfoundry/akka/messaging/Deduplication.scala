package com.productfoundry.akka.messaging

import com.productfoundry.akka.serialization.Persistable

/**
 * Convenience methods to prevent duplicate messages from being handled multiple times.
 */
trait Deduplication {

  /**
   * Track processed ids in memory.
   */
  private var processedDeduplicationIds: Set[String] = Set.empty

  /**
   * Marks the deduplication id as processed.
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

  /**
   * Handles a deduplicatable.
   * @param deduplicatable to handle.
   * @param duplicate function handler.
   * @param unique function handler.
   * @tparam T duplicatable type.
   * @return Unit.
   */
  def processDeduplicatable[T <: Deduplicatable](deduplicatable: T)(duplicate: (T) => Unit)(unique: (T) => Unit): Unit = {
    if (isAlreadyProcessed(deduplicatable.deduplicationId)) duplicate(deduplicatable) else unique(deduplicatable)
  }
}

case class DeduplicationEntry(deduplicationId: String) extends Persistable
