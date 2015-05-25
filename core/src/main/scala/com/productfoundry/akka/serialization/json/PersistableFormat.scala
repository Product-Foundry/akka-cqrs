package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.ConfirmationProtocol
import play.api.libs.json.Format

trait PersistableFormat {

  implicit def AggregateEventFormatToPersistableFormat(implicit aggregateEventFormat: Format[AggregateEvent]): Format[Persistable] = {
    TypeChoiceFormat(
      "Commit" -> CommitFormat.CommitFormat[AggregateEvent],
      "DomainCommit" -> DomainCommitFormat.DomainCommitFormat[AggregateEvent],
      "Confirmed" -> ConfirmationProtocol.Confirmed.ConfirmedFormat
    )
  }
}

object PersistableFormat extends PersistableFormat