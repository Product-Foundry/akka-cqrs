package com.productfoundry.akka.cqrs

import play.api.libs.json.{Format, Json}

/**
 * Uniquely identifies an aggregate revision.
 */
case class AggregateSnapshot(name: String, id: String, revision: AggregateRevision)

object AggregateSnapshot {
  implicit val AggregateSnapshotFormat: Format[AggregateSnapshot] = Json.format[AggregateSnapshot]
}