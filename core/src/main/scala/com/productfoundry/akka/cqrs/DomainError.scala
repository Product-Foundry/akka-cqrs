package com.productfoundry.akka.cqrs

/**
 * Marker trait for user recoverable errors in the aggregate.
 */
trait DomainError extends Serializable