package com.productfoundry.akka.cqrs

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.json.Json

class IdentifierSpec extends WordSpecLike with Matchers with GeneratorDrivenPropertyChecks {

  import IdentifierSpec._

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

    "parse from string" in {
      forAll { testId: TestId =>
        TestId.fromString(testId.toString) should be(Some(testId))
      }
    }
  }

  def arbitraryIdentifiers = Gen.listOf(Arbitrary.arbitrary[TestId])
}

object IdentifierSpec {
  implicit def ArbitraryIdentifier[I <: Identifier](implicit companion: IdentifierCompanion[I]): Arbitrary[I] = Arbitrary(Gen.wrap(companion.generate()))
}
