package com.productfoundry.akka.cqrs

/**
 * Offered to the failure handler by default if an aggregate does not exist.
 * @param id of the aggregate.
 */
case class AggregateUnknown(id: EntityId) extends AggregateError
