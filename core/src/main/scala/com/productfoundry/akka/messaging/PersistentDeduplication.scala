package com.productfoundry.akka.messaging

import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import com.productfoundry.akka.messaging.PersistentDeduplication._
import com.productfoundry.akka.serialization.Persistable

trait PersistentDeduplication
  extends PersistentActor
  with Deduplication
  with ActorLogging {
  
  def receiveCommandAndDeduplicate: Receive = {
    case message if receiveOnce.isDefinedAt(message) =>

      message match {
        case deduplicatable: Deduplicatable =>
          if (isAlreadyProcessed(deduplicatable.deduplicationId)) {
            log.debug("Skipping duplicate: {}", deduplicatable.deduplicationId)
          } else {
            receiveOnce(message)
            persist(DeduplicatableReceived(deduplicatable.deduplicationId))(_ => markAsProcessed(deduplicatable.deduplicationId))
          }

        case _ =>
          receiveOnce(message)
      }
  }

  override def receiveCommand: Receive = receiveCommandAndDeduplicate

  def receiveOnce: Receive

  def receiveRecoverDeduplication: Receive = {
    case DeduplicatableReceived(deduplicationId) =>
      markAsProcessed(deduplicationId)
  }

  override def receiveRecover: Receive = receiveRecoverDeduplication
}

object PersistentDeduplication {

  case class DeduplicatableReceived(deduplicationId: String) extends Persistable
}
