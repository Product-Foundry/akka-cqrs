package com.productfoundry.akka.cqrs

/**
 * Exception when aggregate state is accessed without being initialized.
 */
case class AggregateNotInitializedException(message: String)
  extends AggregateException(message)
