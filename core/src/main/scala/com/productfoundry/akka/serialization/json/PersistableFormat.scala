package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.confirm.ConfirmationProtocol
import com.productfoundry.akka.cqrs.project.domain.DomainCommit
import play.api.libs.json.{Format, Json}

case class PersistableFormat(implicit val eventFormat: Format[AggregateEvent]) {

  implicit val CommitMetadataFormat: Format[CommitMetadata] = Json.format[CommitMetadata]

  implicit val CommitFormat: Format[Commit] = Json.format[Commit]

  implicit val DomainCommitFormat: Format[DomainCommit] = Json.format[DomainCommit]

  implicit val PersistableFormat: Format[Persistable] = {
    TypeChoiceFormat(
      "Commit" -> CommitFormat,
      "DomainCommit" -> DomainCommitFormat,
      "Confirmed" -> ConfirmationProtocol.Confirmed.ConfirmedFormat
    )
  }
}
