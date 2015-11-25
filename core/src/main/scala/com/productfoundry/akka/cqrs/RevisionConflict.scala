package com.productfoundry.akka.cqrs

/**
  * Revision check failure.
  *
  * @param expected revision.
  * @param actual revision.
  */
case class RevisionConflict(expected: AggregateRevision,
                            actual: AggregateRevision) extends AggregateUpdateFailure