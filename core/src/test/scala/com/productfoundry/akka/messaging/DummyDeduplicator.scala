package com.productfoundry.akka.messaging

import akka.actor.Actor
import com.productfoundry.akka.messaging.DummyDeduplicator.DummyDeduplicatable

class DummyDeduplicator(val persistenceId: String) extends Actor with PersistentDeduplication {

  override def receiveOnce: Receive = {
    case DummyDeduplicatable(deduplicationId) => sender() ! deduplicationId
  }
}

object DummyDeduplicator {

  case class DummyDeduplicatable(deduplicationId: String) extends Deduplicatable
}