package com.productfoundry.akka.cqrs

/**
 * Offered to the failure handler by default if an aggregate already exists.
 */
case object AggregateAlreadyInitialized extends AggregateError