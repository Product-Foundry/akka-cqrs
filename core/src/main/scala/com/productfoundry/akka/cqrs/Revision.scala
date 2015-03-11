package com.productfoundry.akka.cqrs

import scala.reflect.ClassTag
import scala.util.Try

trait Revision[R <: Revision[R]] extends Proxy with Ordered[R] with Serializable {
  def value: Long

  require(value >= 0, "revision cannot be negative")

  override def self: Any = value

  override def compare(that: R): Int = value compare that.value

  def next: R
}

abstract class RevisionCompanion[R <: Revision[R]: ClassTag] {

  def apply(value: Long): R

  def fromString(s: String): Option[R] = Try(apply(s.toLong)).toOption

  implicit val RevisionCompanionObject: RevisionCompanion[R] = this

  lazy val Initial = apply(0L)
}
