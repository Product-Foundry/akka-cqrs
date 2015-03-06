package com.productfoundry.akka.cqrs

/**
 * Offered to the failure handler by default if an aggregate does not exist.
 */
case object AggregateNotInitialized extends AggregateError
