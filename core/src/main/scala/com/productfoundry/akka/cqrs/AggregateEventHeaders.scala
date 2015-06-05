package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Json}

/**
 * Contains additional info stored for an aggregate.
 *
 * @param snapshot indicating an aggregate with a specific revision.
 * @param metadata additional info.
 * @param timestamp of creation.
 */
case class AggregateEventHeaders(snapshot: AggregateSnapshot,
                                 metadata: Map[String, String] = Map.empty,
                                 timestamp: Long = System.currentTimeMillis())

object AggregateEventHeaders {

  implicit val AggregateEventHeadersFormat: Format[AggregateEventHeaders] = Json.format[AggregateEventHeaders]
}
