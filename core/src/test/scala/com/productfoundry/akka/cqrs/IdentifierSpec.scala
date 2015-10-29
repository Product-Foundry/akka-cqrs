package com.productfoundry.akka.cqrs

import com.productfoundry.support.Spec
import com.productfoundry.support.TestSupport._

class IdentifierSpec extends Spec with Fixtures {

  "Identifiers" must {

    "be unique" in {
      forAll { testIds: List[DummyId] =>
         testIds.toSet.size should be(testIds.size)
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
