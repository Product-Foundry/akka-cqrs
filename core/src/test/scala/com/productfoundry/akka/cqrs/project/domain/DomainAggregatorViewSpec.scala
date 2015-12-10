package com.productfoundry.akka.cqrs.project.domain

import akka.actor.Props
import com.productfoundry.akka.cqrs.project.{EventCollector, ProjectionRevision}
import com.productfoundry.akka.cqrs.{Commit, Fixtures}
import com.productfoundry.support.PersistenceTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.concurrent.Await
import scala.concurrent.duration._

class DomainAggregatorViewSpec extends PersistenceTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  "Domain aggregator view" must {

    "recover all aggregated events" in new fixture {
      forAll { commit: Commit =>
        domainRevision = commit.records.foldLeft(domainRevision) { case (_, eventRecord) =>
          domainAggregator ! eventRecord
          expectMsgType[ProjectionRevision]
        }

        val stateWithRevisionFuture = domainAggregatorView.getWithRevision(domainRevision)
        val (_, revision) = Await.result(stateWithRevisionFuture, 5.seconds)
        revision should be(domainRevision)
      }

      // Just make sure we tested something after all
      domainRevision should be > ProjectionRevision.Initial
    }
  }

  trait fixture {

    val persistenceId = randomPersistenceId

    val domainAggregator = system.actorOf(Props(classOf[DomainAggregator], persistenceId, Integer.MAX_VALUE))

    val recoveryThreshold = 1.millis

    var domainRevision = ProjectionRevision.Initial

    val domainAggregatorView = DomainAggregatorView(system, persistenceId)(EventCollector())(recoveryThreshold)
  }
}
