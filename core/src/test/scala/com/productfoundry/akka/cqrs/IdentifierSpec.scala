package com.productfoundry.akka.cqrs

import com.productfoundry.support.Spec
import com.productfoundry.support.TestSupport._
import play.api.libs.json.Json

class IdentifierSpec extends Spec with Fixtures {

  "Identifiers" must {

    "be unique" in {
      forAll { testIds: List[DummyId] =>
         testIds.toSet.size should be(testIds.size)
      }
    }

    "serialize to/from json" in {
      forAll { testId: DummyId =>
        Json.toJson(testId).as[DummyId] should be(testId)
      }
    }

    "be serializable" in {
      forAll { testId: DummyId =>
        deserializeBytes(serializeBytes(testId)) should be (testId)
      }
    }

    "parse from string" in {
      forAll { testId: DummyId =>
        DummyId.fromString(testId.toString) should be(Some(testId))
      }
    }
  }
}
