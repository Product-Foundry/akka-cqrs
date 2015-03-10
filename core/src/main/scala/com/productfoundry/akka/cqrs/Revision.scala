package com.productfoundry.akka.cqrs

import play.api.libs.json.Format

import scala.reflect.ClassTag
import scala.util.Try

abstract class Revision[R <: Revision[R] : RevisionCompanion] extends Proxy with Ordered[R] {
  def value: Long

  require(value >= 0, "revision cannot be negative")

  override def self: Any = value

  override def compare(that: R): Int = value compare that.value

  def next = implicitly[RevisionCompanion[R]].apply(value + 1)
}

abstract class RevisionCompanion[R <: Revision[R]: ClassTag] {

  def apply(value: Long): R

  def fromString(s: String): Option[R] = Try(apply(s.toLong)).toOption

  implicit val RevisionCompanionObject: RevisionCompanion[R] = this

  implicit val RevisionFormat: Format[R] = JsonMapping.valueFormat(apply)(_.value)

  lazy val Initial = apply(0L)
}
