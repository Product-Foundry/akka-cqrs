package com.productfoundry.akka.messaging

/**
 * Message that can be deduplicated.
 */
trait Deduplicatable {

  /**
   * Used for deduplication.
   */
  def deduplicationId: String
}
