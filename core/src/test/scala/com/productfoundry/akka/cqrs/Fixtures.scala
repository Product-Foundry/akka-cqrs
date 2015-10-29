package com.productfoundry.akka.cqrs

import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}

trait Fixtures {

  implicit def ArbitraryDummyId: Arbitrary[DummyId] = Arbitrary {
    Gen.wrap(DummyId.generate())
  }

  implicit def ArbitraryRevision[R <: Revision[R]](implicit companion: RevisionCompanion[R]): Arbitrary[R] = Arbitrary {
    Gen.choose(0, 100).map(value => companion.apply(value.toLong))
  }

  implicit def ArbitraryCommit: Arbitrary[Commit] = Arbitrary {
    for {
      id <- arbitrary[DummyId]
      revision <- arbitrary[AggregateRevision]
      values <- arbitrary[Seq[Int]]
    } yield Changes(values.map(value => DummyEvent(id, value)): _*).createCommit(AggregateTag("", id.toString, revision))
  }

  implicit def ArbitraryConfirmable: Arbitrary[DummyConfirmable] = Arbitrary {
    arbitrary[Long].map(value => DummyConfirmable(value))
  }

  implicit def ArbitraryAggregateTag: Arbitrary[AggregateTag] = Arbitrary {
    for {
      name <- arbitrary[String]
      id <- arbitrary[DummyId]
      revision <- arbitrary[AggregateRevision]
    } yield AggregateTag(name, id.toString, revision)
  }
}
