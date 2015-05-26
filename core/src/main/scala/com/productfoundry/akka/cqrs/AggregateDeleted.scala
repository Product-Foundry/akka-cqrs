package com.productfoundry.akka.cqrs

/**
 * Offered to the failure handler if an aggregate is deleted.
 */
case object AggregateDeleted extends AggregateError