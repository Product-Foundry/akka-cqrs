package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs.AggregateEvent
import com.productfoundry.akka.cqrs.project.DomainCommit
import play.api.libs.json._

trait DomainCommitFormat {

  import CommitFormat._

  implicit def DomainCommitFormat(implicit eventFormat: Format[AggregateEvent]): Format[DomainCommit] = Json.format[DomainCommit]
}

object DomainCommitFormat extends DomainCommitFormat