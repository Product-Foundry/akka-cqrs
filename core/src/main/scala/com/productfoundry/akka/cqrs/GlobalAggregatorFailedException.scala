package com.productfoundry.akka.cqrs

/**
 * Thrown when commit aggregation failed.
 * @param commitResult the result of the aggregate commit, which is not aggregated globally.
 */
case class GlobalAggregatorFailedException(commitResult: CommitResult) extends RuntimeException