package com.productfoundry.akka.cqrs

/**
 * Base event marker trait.
 */
trait DomainEvent {
  def aggregateId: AggregateId
}