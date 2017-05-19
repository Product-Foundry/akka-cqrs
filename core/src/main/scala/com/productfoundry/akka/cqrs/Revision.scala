package com.productfoundry.akka.cqrs

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
   * @return The next revision
   */
  def next(implicit companion: RevisionCompanion[R]): R = companion.apply(value + 1L)

  /**
   * @return Infinite stream of upcoming revisions, starting with the next revision
   */
  def upcoming(implicit companion: RevisionCompanion[R]): Stream[R] = Stream.iterate(next)(_.next)
}

abstract class RevisionCompanion[R <: Revision[R] : ClassTag] {

  def apply(value: Long): R

  def fromString(s: String): Option[R] = Try(apply(s.toLong)).toOption

  implicit val RevisionCompanionObject: RevisionCompanion[R] = this

  lazy val Initial: R = apply(0L)
}
