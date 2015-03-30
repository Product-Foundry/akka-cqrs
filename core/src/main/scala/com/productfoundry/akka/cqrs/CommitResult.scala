package com.productfoundry.akka.cqrs

/**
 * Indicates a successful commit.
 *
 * @param aggregateRevision of the aggregate.
 * @param domainRevision of the domain.
 * @param payload as specified by the aggregate to be sent with the commit result.
 */
case class CommitResult(aggregateRevision: AggregateRevision, domainRevision: DomainRevision, payload: Any = Unit)
