package com.productfoundry.akka.cqrs

/**
 * Exception when a create is attempted for an aggregate that already exists.
 */
case class AggregateAlreadyInitializedException(revision: AggregateRevision)
  extends AggregateException(s"Aggregate already initialized at revision $revision")
