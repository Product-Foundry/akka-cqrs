package com.productfoundry.akka.cqrs

import play.api.libs.json.Format

/**
 * The revision of the aggregator.
 */
final case class DomainRevision(value: Long) extends Proxy with Ordered[DomainRevision] {
  require(value >= 0, "domain revision cannot be negative")

  override def self: Any = value

  override def compare(that: DomainRevision): Int = value compare that.value

  def next = DomainRevision(value + 1)
}

object DomainRevision {
  val Initial = DomainRevision(0)

  implicit val JsonFormat: Format[DomainRevision] = JsonMapping.valueFormat(apply)(_.value)
}
