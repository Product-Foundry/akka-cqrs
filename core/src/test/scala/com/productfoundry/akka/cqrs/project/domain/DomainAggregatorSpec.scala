package com.productfoundry.akka.cqrs.project.domain

import akka.actor.Props
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.project.{ProjectionUpdate, ProjectionUpdateResponseHandler}
import com.productfoundry.support.PersistenceTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class DomainAggregatorSpec extends PersistenceTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  val snapshotInterval = 10

  "Domain aggregator" must {
    "persist commit" in new fixture {
      forAll { commit: Commit =>
        commit.records.foreach { eventRecord =>
          domainAggregator ! eventRecord
          expectMsgType[ProjectionUpdate]
        }
      }
    }
  }

  trait fixture extends {
    val persistenceId = DummyId.generate().toString

    val domainAggregatorProps = Props(new DomainAggregator(persistenceId, snapshotInterval) with ProjectionUpdateResponseHandler)

    val domainAggregator = system.actorOf(domainAggregatorProps)
  }
}
