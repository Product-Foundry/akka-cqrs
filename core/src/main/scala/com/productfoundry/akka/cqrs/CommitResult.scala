package com.productfoundry.akka.cqrs

/**
 * Indicates a successful commit.
 *
 * @param revision of the aggregate.
 * @param payload as specified by the aggregate to be sent with the commit result.
 */
case class CommitResult(revision: AggregateRevision, payload: Any = Unit)
