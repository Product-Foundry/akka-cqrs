package com.productfoundry.akka.cqrs

/**
 * The revision of the aggregate.
 */
final case class AggregateRevision(value: Long) extends Proxy with Ordered[AggregateRevision] {
  require(value >= 0, "aggregate revision cannot be negative")

  override def self: Any = value

  override def compare(that: AggregateRevision): Int = value compare that.value

  def next = AggregateRevision(value + 1)
}

object AggregateRevision {
  val Initial = AggregateRevision(0)
}
