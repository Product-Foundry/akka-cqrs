package com.productfoundry.akka.cqrs

import com.productfoundry.support.Spec

class RevisionSpec extends Spec with Fixtures {

  "Revisions" must {

    "parse from string" in {
      forAll { revision: TestRevision =>
        TestRevision.fromString(revision.toString()) should be(Some(revision))
      }
    }

    "be sorted by value" in {
      forAll { revisions: List[TestRevision] =>
        revisions.sorted.map(_.value) should be(revisions.map(_.value).sorted)
      }
    }

    "determine next value" in {
      forAll { revision: TestRevision =>
        revision.next should be(revision.value + 1L)
      }
    }

    "determine upcoming values" in {
      forAll { revision: TestRevision =>
        val upcoming = revision.upcoming.iterator
        upcoming.next() should be(revision.value + 1L)
        upcoming.next() should be(revision.value + 2L)
        upcoming.next() should be(revision.value + 3L)
      }
    }

    "have initial value" in {
      TestRevision.Initial.value should be(0L)
    }

    "not be negative" in {
      an [IllegalArgumentException] should be thrownBy TestRevision(-1)
    }
  }
}
