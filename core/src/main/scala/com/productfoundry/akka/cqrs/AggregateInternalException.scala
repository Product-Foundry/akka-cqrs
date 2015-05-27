package com.productfoundry.akka.cqrs

/**
 * Exception indicating an internal problem with the aggregate, probably caused by a programming error.
 */
case class AggregateInternalException(message: String)
  extends AggregateException(message)
