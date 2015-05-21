package com.productfoundry.akka.cqrs

/**
 * Base event marker trait.
 */
trait AggregateEvent extends AggregateMessage with DomainEvent