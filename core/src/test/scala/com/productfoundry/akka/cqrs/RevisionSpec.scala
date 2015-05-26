package com.productfoundry.akka.cqrs

import com.productfoundry.support.TestSupport._
import com.productfoundry.support.Spec
import org.scalacheck.{Arbitrary, Gen}
import play.api.libs.json.Json

class RevisionSpec extends Spec {

  import RevisionSpec._

  "Revisions" must {

    "serialize to/from json" in {
      forAll { revision: TestRevision =>
        Json.toJson(revision).as[TestRevision] should be(revision)
      }
    }

    "be serializable" in {
      forAll { revision: TestRevision =>
        deserializeBytes(serializeBytes(revision)) should be(revision)
      }
    }

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

    "have initial value" in {
      TestRevision.Initial.value should be(0L)
    }

    "not be negative" in {
      an [IllegalArgumentException] should be thrownBy TestRevision(-1)
    }
  }
}

object RevisionSpec {
  implicit def ArbitraryRevision[R <: Revision[R]](implicit companion: RevisionCompanion[R]): Arbitrary[R] = Arbitrary {
    Gen.choose(0, 100).map(value => companion.apply(value.toLong))
  }
}
