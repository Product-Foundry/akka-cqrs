package com.productfoundry.akka.messaging

import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import com.productfoundry.akka.messaging.PersistentDeduplication._
import com.productfoundry.akka.serialization.Persistable

/**
 * Supports deduplication handling, where ids of handled messages are also persisted.
 */
trait PersistentDeduplication
  extends PersistentActor
  with DeduplicationHandler
  with ActorLogging {

  override def uniqueMessageReceived(deduplicationId: String): Unit = {
    if (recoveryFinished) {
      // TODO [AK] Deduplication is not properly guaranteed in this implementation
      persistAsync(Processed(deduplicationId))(_ => Unit)
      markAsProcessed(deduplicationId)
    }
  }

  override def receiveCommand: Receive = receiveDuplicate

  def receiveRecoverProcessed: Receive = {
    case Processed(deduplicationId) => markAsProcessed(deduplicationId)
  }

  override def receiveRecover: Receive = receiveRecoverProcessed
}

object PersistentDeduplication {

  case class Processed(deduplicationId: String) extends Persistable

}
