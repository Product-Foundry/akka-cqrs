package com.productfoundry.akka.cqrs

/**
 * An aggregate command requires a revision check, but no revision is specified.
 */
case object AggregateRevisionExpected extends AggregateError
