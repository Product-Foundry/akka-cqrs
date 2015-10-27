package com.productfoundry.akka.cqrs.project.domain

import akka.actor.Props
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.support.PersistenceTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class DomainAggregatorSpec extends PersistenceTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  "Domain aggregator" must {
    "persist commit" in new fixture {
      forAll { commit: Commit =>
        commit.records.par.foreach { eventRecord =>
          domainAggregator ! eventRecord
        }

        val revisions = commit.records.foldLeft(Vector.empty[ProjectionRevision]){ (acc, eventRecord) =>
          acc :+ expectMsgType[ProjectionRevision]
        }

        revisions.groupBy(revision => revision).filter(_._2.size > 1) should have size 0
      }
    }
  }

  trait fixture extends {
    val persistenceId = DummyId.generate().toString

    val domainAggregatorProps = Props(new DomainAggregator(persistenceId))

    val domainAggregator = system.actorOf(domainAggregatorProps)
  }
}
