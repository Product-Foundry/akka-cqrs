package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs._
import com.productfoundry.support.Spec

class EventProjectionSpec extends Spec with Fixtures {

  "Event projection" must {

    "provide access to projected commit" in {
      forAll { commit: Commit =>
        CommitCollector().project(commit).commits should contain(commit)
      }
    }

    "provide access to projected events" in {
      forAll { commit: Commit =>
        EventCollector().project(commit).events should contain theSameElementsAs commit.records.map(_.event)
      }
    }
  }
}
