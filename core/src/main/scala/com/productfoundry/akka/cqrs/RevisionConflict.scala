package com.productfoundry.akka.cqrs

/**
 * Revision check failure.
 *
 * @param entityId The entityId.
 * @param expected revision.
 * @param actual revision.
 */
case class RevisionConflict(entityId: EntityId, expected: AggregateRevision, actual: AggregateRevision, commits: Seq[Commit[DomainEvent]] = Seq.empty) extends AggregateError