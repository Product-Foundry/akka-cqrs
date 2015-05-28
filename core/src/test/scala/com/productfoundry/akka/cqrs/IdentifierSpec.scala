package com.productfoundry.akka.cqrs

import com.productfoundry.support.Spec
import com.productfoundry.support.TestSupport._
import play.api.libs.json.Json

class IdentifierSpec extends Spec with Fixtures {

  "Identifiers" must {

    "be unique" in {
      forAll { testIds: List[TestId] =>
         testIds.toSet.size should be(testIds.size)
      }
    }

    "serialize to/from json" in {
      forAll { testId: TestId =>
        Json.toJson(testId).as[TestId] should be(testId)
      }
    }

    "be serializable" in {
      forAll { testId: TestId =>
        deserializeBytes(serializeBytes(testId)) should be (testId)
      }
    }

    "parse from string" in {
      forAll { testId: TestId =>
        TestId.fromString(testId.toString) should be(Some(testId))
      }
    }
  }
}
