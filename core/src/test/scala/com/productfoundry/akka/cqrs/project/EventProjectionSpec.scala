package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs._
import com.productfoundry.support.Spec

class EventProjectionSpec extends Spec with Fixtures {

  "Event projection" must {

    "provide access to projected events" in {
      forAll { commit: Commit =>
        val eventCollector = commit.records.foldLeft(EventCollector()) { case (acc, eventRecord) =>
          acc.project(ProjectionRevision.Initial, eventRecord)
        }
        eventCollector.events should contain theSameElementsAs commit.records.map(_.event)
      }
    }
  }
}
