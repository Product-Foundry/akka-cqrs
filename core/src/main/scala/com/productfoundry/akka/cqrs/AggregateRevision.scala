package com.productfoundry.akka.cqrs

/**
 * The revision of the aggregate.
 */
case class AggregateRevision(value: Long) extends Revision[AggregateRevision]

object AggregateRevision extends RevisionCompanion[AggregateRevision]
