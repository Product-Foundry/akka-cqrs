package com.productfoundry.akka.cqrs

/**
 * Indicates a successful commit.
 *
 * @param aggregateRevision of the aggregate.
 * @param domainRevision of the domain.
 */
case class CommitResult(aggregateRevision: AggregateRevision, domainRevision: DomainRevision)
