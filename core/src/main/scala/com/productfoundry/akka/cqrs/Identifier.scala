package com.productfoundry.akka.cqrs

import java.util.UUID

import play.api.libs.json.{Reads, Writes, Format}

import scala.reflect.ClassTag
import scala.util.Try

/**
 * Identifier backed by uuid.
 */
trait Identifier {
  def uuid: Uuid

  override def toString: String = uuid.toString
}

/**
 * Identifier Companion.
 */
abstract class IdentifierCompanion[I <: Identifier: ClassTag] {

  val prefix = implicitly[ClassTag[I]].runtimeClass.getSimpleName

  def apply(uuid: Uuid): I

  def apply(s: String): I = fromString(s).getOrElse(throw new IllegalArgumentException(s))

  def apply(identifier: Identifier): I = apply(identifier.uuid)

  def generate(): I = apply(UUID.randomUUID)

  def fromString(s: String): Option[I] = s match {
    case IdentifierRegex(uuid) => Try(apply(UUID.fromString(uuid))).toOption
    case _ => None
  }

  implicit val IdentifierFormat: Format[I] = Format(Reads.of[Uuid].map(apply), Writes(a => Writes.of[Uuid].writes(a.uuid)))

  implicit val IdentifierCompanionObject: IdentifierCompanion[I] = this

  private val IdentifierRegex = """([a-fA-F0-9-]{36})""".r
}
