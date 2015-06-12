package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Json}

/**
 * Uniquely identifies an aggregate revision.
 */
case class AggregateTag(name: String, id: String, revision: AggregateRevision) {

  /**
   * Unique tag value.
   */
  lazy val value: String = s"$name:$id:$revision"

  override def toString: String = value
}

object AggregateTag {
  implicit val AggregateTagFormat: Format[AggregateTag] = Json.format[AggregateTag]
}