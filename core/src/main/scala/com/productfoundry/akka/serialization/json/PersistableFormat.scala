package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.project.domain.DomainCommit
import com.productfoundry.akka.messaging.Confirmable.Confirmed
import com.productfoundry.akka.messaging.Deduplication.Received
import com.productfoundry.akka.serialization.Persistable
import play.api.libs.json.{Format, Json}

case class PersistableFormat(implicit val eventFormat: Format[AggregateEvent]) {

  implicit val AggregateEventRecordFormat: Format[AggregateEventRecord] = Json.format[AggregateEventRecord]

  implicit val CommitEntryFormat: Format[CommitEntry] = Json.format[CommitEntry]

  implicit val CommitFormat: Format[Commit] = Json.format[Commit]

  implicit val DomainCommitFormat: Format[DomainCommit] = Json.format[DomainCommit]

  implicit val PersistableFormat: Format[Persistable] = {
    TypeChoiceFormat(
      "Commit" -> CommitFormat,
      "DomainCommit" -> DomainCommitFormat,
      "Confirmed" -> Confirmed.ConfirmedFormat,
      "Received" -> Received.ReceivedFormat
    )
  }
}
