package com.productfoundry.akka.cqrs

/**
 * Revision check failure.
 *
 * @param aggregateId The aggregateId.
 * @param expected revision.
 * @param actual revision.
 */
case class RevisionConflict(aggregateId: AggregateId, expected: AggregateRevision, actual: AggregateRevision, commits: Seq[Commit[DomainEvent]] = Seq.empty) extends AggregateError