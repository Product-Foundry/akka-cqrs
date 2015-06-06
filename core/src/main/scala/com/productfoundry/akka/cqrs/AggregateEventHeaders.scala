package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Json}

/**
 * Contains additional info stored for an aggregate.
 *
 * @param tag indicating an aggregate with a specific revision.
 * @param metadata additional info.
 * @param timestamp of creation.
 */
case class AggregateEventHeaders(tag: AggregateTag,
                                 metadata: Map[String, String] = Map.empty,
                                 timestamp: Long = System.currentTimeMillis())

object AggregateEventHeaders {

  implicit val AggregateEventHeadersFormat: Format[AggregateEventHeaders] = Json.format[AggregateEventHeaders]
}
