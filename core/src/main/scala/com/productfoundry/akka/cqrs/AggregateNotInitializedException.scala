package com.productfoundry.akka.cqrs

/**
 * Exception when aggregate state is accessed without being initialized.
 */
case class AggregateNotInitializedException(event: AggregateEvent)
  extends AggregateException(s"Unable to initialize aggregate with $event")
