package com.productfoundry.akka.cqrs

import org.scalacheck.{Gen, Arbitrary}

import Arbitrary._

trait Fixtures {

  implicit def ArbitraryIdentifier[I <: Identifier](implicit companion: IdentifierCompanion[I]): Arbitrary[I] = Arbitrary {
    Gen.wrap(companion.generate())
  }

  implicit def ArbitraryRevision[R <: Revision[R]](implicit companion: RevisionCompanion[R]): Arbitrary[R] = Arbitrary {
    Gen.choose(0, 100).map(value => companion.apply(value.toLong))
  }

  implicit def ArbitraryCommit: Arbitrary[Commit] = Arbitrary {
    for {
      id <- arbitrary[TestId]
      revision <- arbitrary[AggregateRevision]
      values <- arbitrary[Seq[Int]]
    } yield Commit(CommitMetadata(id.toString, revision), values.map(value => TestEvent(id, value)))
  }
}
