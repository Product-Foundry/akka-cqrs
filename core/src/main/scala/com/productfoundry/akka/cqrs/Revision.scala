package com.productfoundry.akka.cqrs

import play.api.libs.json.{Writes, Reads, Format}

import scala.reflect.ClassTag
import scala.util.Try

trait Revision[R <: Revision[R]] extends Proxy with Ordered[R] with Serializable {
  def value: Long

  require(value >= 0L, "revision cannot be negative")

  override def self: Any = value

  override def compare(that: R): Int = value compare that.value

  def next(implicit companion: RevisionCompanion[R]): R = companion.apply(value + 1L)

}

abstract class RevisionCompanion[R <: Revision[R]: ClassTag] {

  def apply(value: Long): R

  def fromString(s: String): Option[R] = Try(apply(s.toLong)).toOption

  implicit val RevisionCompanionObject: RevisionCompanion[R] = this

  implicit val RevisionFormat: Format[R] = Format(Reads.of[Long].map(apply), Writes(a => Writes.of[Long].writes(a.value)))

  lazy val Initial = apply(0L)
}
