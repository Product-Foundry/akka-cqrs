package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs.ConfirmationProtocol.Confirmed
import com.productfoundry.akka.cqrs._
import play.api.libs.json.{Json, Format}

trait PersistableFormat {

  implicit def AggregateEventFormatToPersistableFormat(implicit aggregateEventFormat: Format[AggregateEvent]): Format[Persistable] = {

    TypeChoiceFormat(
      "Commit" -> CommitFormat.CommitFormat[AggregateEvent],
      "DomainCommit" -> DomainCommitFormat.DomainCommitFormat[AggregateEvent],
      "Confirmed" -> Json.format[Confirmed]
    )
  }
}

object PersistableFormat extends PersistableFormat