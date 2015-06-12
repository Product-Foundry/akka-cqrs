package com.productfoundry.akka.cqrs

import com.productfoundry.support.Spec

import scala.util.Random

class AggregateTagSpec extends Spec with Fixtures {

  "Aggregate tags" must {

    "have unique handle" in {
      forAll { aggregateTags: Set[AggregateTag] =>
        aggregateTags.map(_.value).size should be(aggregateTags.size)
      }
    }

    "have stable handle" in {
      forAll { aggregateTag: AggregateTag =>
        Stream.continually(aggregateTag.copy()).take(Random.nextInt(10) + 1).toSet should have size 1
      }
    }
  }
}