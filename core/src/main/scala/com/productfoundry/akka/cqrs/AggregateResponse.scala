package com.productfoundry.akka.cqrs

/**
 * Sent back after a successful update.
 *
 * @param revision of the aggregate.
 * @param payload as specified by the aggregate to be sent with the commit result.
 */
case class AggregateResponse(revision: AggregateRevision, payload: Any = Unit)
