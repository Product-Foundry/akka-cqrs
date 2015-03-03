package com.productfoundry.akka.cqrs

/**
 * The revision of the aggregator.
 */
final case class GlobalRevision(value: Long) extends Proxy with Ordered[GlobalRevision] {
  require(value >= 0, "global revision cannot be negative")

  override def self: Any = value

  override def compare(that: GlobalRevision): Int = value compare that.value

  def next = GlobalRevision(value + 1)
}

object GlobalRevision {
  val Initial = GlobalRevision(0)
}
