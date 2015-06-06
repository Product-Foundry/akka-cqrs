package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Reads, Writes}

import scala.reflect.ClassTag
import scala.util.Try

trait Revision[R <: Revision[R]] extends Proxy with Ordered[R] with Serializable {

  /**
   * The actual revision is backed by a long value.
   */
  def value: Long

  require(value >= 0L, "revision cannot be negative")

  override def self: Any = value

  override def compare(that: R): Int = value compare that.value

  /**
   * Returns the next evision
   * @param companion
   * @return
   */
  def next(implicit companion: RevisionCompanion[R]): R = companion.apply(value + 1L)

  def upcoming(implicit companion: RevisionCompanion[R]): Stream[R] = Stream.iterate(next)(_.next)
}

abstract class RevisionCompanion[R <: Revision[R] : ClassTag] {

  def apply(value: Long): R

  def fromString(s: String): Option[R] = Try(apply(s.toLong)).toOption

  implicit val RevisionCompanionObject: RevisionCompanion[R] = this

  implicit val RevisionFormat: Format[R] = Format(Reads.of[Long].map(apply), Writes(a => Writes.of[Long].writes(a.value)))

  lazy val Initial = apply(0L)
}
