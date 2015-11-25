package com.productfoundry.akka.cqrs

/**
 * Marker trait for user recoverable failures in the aggregate.
 */
trait AggregateUpdateFailure extends Serializable