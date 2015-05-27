package com.productfoundry.akka.cqrs

/**
 * Exception when a message is sent to an aggregate that is deleted.
 */
case class AggregateDeletedException(revision: AggregateRevision)
  extends AggregateException(s"Aggregate deleted at revision $revision")