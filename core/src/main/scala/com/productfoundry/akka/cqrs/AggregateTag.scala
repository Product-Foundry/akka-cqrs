package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Json}

/**
 * Uniquely identifies an aggregate revision.
 */
case class AggregateTag(name: String, id: String, revision: AggregateRevision) {

  /**
   * Unique handle for the tag.
   */
  lazy val handle: String = s"$name:$id:$revision"
}

object AggregateTag {
  implicit val AggregateTagFormat: Format[AggregateTag] = Json.format[AggregateTag]
}