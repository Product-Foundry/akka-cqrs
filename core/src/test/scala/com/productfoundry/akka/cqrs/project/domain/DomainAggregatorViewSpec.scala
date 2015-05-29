package com.productfoundry.akka.cqrs.project.domain

import akka.actor.Props
import com.productfoundry.akka.cqrs.project.CommitCollector
import com.productfoundry.akka.cqrs.project.domain.DomainAggregator.DomainAggregatorRevision
import com.productfoundry.akka.cqrs.{Commit, Fixtures}
import com.productfoundry.support.PersistenceTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.concurrent.Await
import scala.concurrent.duration._

class DomainAggregatorViewSpec extends PersistenceTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  "Domain aggregator view" must {

    "recover all aggregated commits" in new fixture {
      forAll { commits: List[Commit] =>
        domainRevision = commits.foldLeft(domainRevision) { case (_, commit) =>
          domainAggregator ! commit
          expectMsgType[DomainAggregatorRevision].revision
        }

        val stateWithRevisionFuture = domainAggregatorView.getWithRevision(domainRevision)
        val (collector, revision) = Await.result(stateWithRevisionFuture, 5.seconds)
        revision should be(domainRevision)
        collector.commits.takeRight(commits.length) should contain theSameElementsAs commits
      }

      // Just make sure we tested something after all
      domainRevision should be > DomainRevision.Initial
    }
  }

  trait fixture {

    val persistenceId = randomPersistenceId

    val domainAggregator = system.actorOf(Props(new DomainAggregator(persistenceId)))

    val recoveryThreshold = 1.millis

    var domainRevision = DomainRevision.Initial

    val domainAggregatorView = DomainAggregatorView(system, persistenceId)(CommitCollector())(recoveryThreshold)
  }

}
