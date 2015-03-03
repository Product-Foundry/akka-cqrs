package com.productfoundry.akka.cqrs

/**
 * Indicates a successful commit.
 *
 * @param aggregateRevision of the aggregate.
 * @param globalRevision of the global aggregator.
 */
case class CommitResult(aggregateRevision: AggregateRevision, globalRevision: GlobalRevision)
