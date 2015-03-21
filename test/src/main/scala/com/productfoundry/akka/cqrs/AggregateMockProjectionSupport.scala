package com.productfoundry.akka.cqrs

import akka.actor.ActorSystem

import scala.concurrent.stm._

/**
 * Base spec for Aggregate projection unit tests.
 */
abstract class AggregateMockProjectionSupport(_system: ActorSystem)
  extends AggregateMockSupport(_system) {

  trait ProjectionFixture[P <: Projection[P]] extends AggregateFactoryFixture {

    /**
     * Tracks application state for every individual spec.
     */
    val projectionRef: Ref[P]

    /**
     * Atomically provides application state using STM.
     */
    val projection: ProjectionProvider[P] = new ProjectionProvider[P] {

      override def getWithRevision(minimum: DomainRevision): (P, DomainRevision) = {
        atomic { implicit txn =>
          if (domainRevisionRef() < minimum) {
            retry
          } else {
            (projectionRef(), domainRevisionRef())
          }
        }
      }

      override def get: P = projectionRef.single.get
    }

    override def update[E <: AggregateEvent](event: E)(implicit txn: InTxn): Unit = {
      projectionRef.transform(_.project(AggregateRevision.Initial)(event))
    }
  }
}
