package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs.project.{DomainCommit, DomainRevision}
import com.productfoundry.akka.cqrs.{AggregateEvent, Commit}
import play.api.libs.json._

trait DomainCommitFormat {

  import play.api.libs.functional.syntax._

  import CommitFormat._

  implicit def DomainCommitFormat[E <: AggregateEvent : Format]: Format[DomainCommit[E]] = (
    (__ \ "revision").format[DomainRevision] and
      (__ \ "timestamp").format[Long] and
      (__ \ "commit").format[Commit[E]]
    )(DomainCommit.apply[E], c => DomainCommit.unapply(c).get)
}

object DomainCommitFormat extends DomainCommitFormat