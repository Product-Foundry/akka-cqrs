package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.project.DomainCommit
import com.productfoundry.akka.cqrs.publish.ConfirmationProtocol
import play.api.libs.json.{Format, Json}

trait PersistableFormat {

  implicit def CommitFormat(implicit eventFormat: Format[AggregateEvent]): Format[Commit] = Json.format[Commit]

  implicit def DomainCommitFormat(implicit eventFormat: Format[AggregateEvent]): Format[DomainCommit] = Json.format[DomainCommit]

  implicit def AggregateEventFormatToPersistableFormat(implicit eventFormat: Format[AggregateEvent]): Format[Persistable] = {
    TypeChoiceFormat(
      "Commit" -> CommitFormat,
      "DomainCommit" -> DomainCommitFormat,
      "Confirmed" -> ConfirmationProtocol.Confirmed.ConfirmedFormat
    )
  }
}

object PersistableFormat extends PersistableFormat