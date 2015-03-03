package com.productfoundry.akka.cqrs

/**
 * Offered to the failure handler by default if an aggregate already exists.
 * @param id of the aggregate.
 */
case class AggregateAlreadyExists(id: EntityId) extends AggregateError