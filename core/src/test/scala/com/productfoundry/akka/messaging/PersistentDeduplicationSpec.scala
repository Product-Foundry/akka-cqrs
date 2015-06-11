package com.productfoundry.akka.messaging

import akka.actor.Props
import com.productfoundry.akka.messaging.DummyDeduplicator.DummyDeduplicatable
import com.productfoundry.support.AggregateTestSupport

class PersistentDeduplicationSpec extends AggregateTestSupport {

  "Deduplication" must {

    "deliver messages" in new fixture {
      deduplicator ! DummyDeduplicatable("1")
      expectMsg("1")
    }

    "deliver multiple messages" in new fixture {
      deduplicator ! DummyDeduplicatable("1")
      expectMsg("1")

      deduplicator ! DummyDeduplicatable("2")
      expectMsg("2")
    }

    "deduplicate messages" in new fixture {
      deduplicator ! DummyDeduplicatable("1")
      expectMsg("1")

      deduplicator ! DummyDeduplicatable("1")
      expectNoMsg()
    }
  }

  trait fixture {
    val persistenceId = randomPersistenceId
    val deduplicator = system.actorOf(Props(new DummyDeduplicator(persistenceId)))
  }
}
