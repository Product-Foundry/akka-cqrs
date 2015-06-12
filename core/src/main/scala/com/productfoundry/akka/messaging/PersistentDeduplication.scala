package com.productfoundry.akka.messaging

import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import com.productfoundry.akka.messaging.PersistentDeduplication._
import com.productfoundry.akka.serialization.Persistable

trait PersistentDeduplication
  extends PersistentActor
  with DeduplicationHandler
  with ActorLogging {

  override def uniqueMessageReceived(deduplicationId: String): Unit = {
    self ! MarkAsProcessed(deduplicationId)
  }

  override def receiveDuplicate: Receive = super.receiveDuplicate orElse {
    case MarkAsProcessed(deduplicationId) => persist(Processed(deduplicationId)) { _ =>
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

  case class MarkAsProcessed(deduplicationId: String)

  case class Processed(deduplicationId: String) extends Persistable

}
