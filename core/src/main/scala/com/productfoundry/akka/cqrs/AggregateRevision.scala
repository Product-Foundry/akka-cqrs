package com.productfoundry.akka.cqrs

/**
 * The revision of the aggregate.
 */
case class AggregateRevision(value: Long) extends Revision[AggregateRevision] {
  override def next: AggregateRevision = AggregateRevision(value + 1)
}

object AggregateRevision extends RevisionCompanion[AggregateRevision]
