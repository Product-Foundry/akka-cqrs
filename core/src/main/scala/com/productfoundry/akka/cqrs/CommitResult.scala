package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.project.DomainRevision

/**
 * Indicates a successful commit.
 *
 * @param aggregateRevision of the aggregate.
 * @param domainRevision of the domain.
 * @param payload as specified by the aggregate to be sent with the commit result.
 */
// TODO [AK] Domain revision does not belong here.
case class CommitResult(aggregateRevision: AggregateRevision, domainRevision: DomainRevision, payload: Any = Unit)
