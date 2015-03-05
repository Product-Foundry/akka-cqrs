package com.productfoundry.akka.cqrs

/**
 * Thrown when commit aggregation failed.
 * @param commitResult the result of the aggregate commit.
 */
case class DomainAggregatorFailedException(commitResult: CommitResult) extends RuntimeException