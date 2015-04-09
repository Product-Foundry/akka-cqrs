package com.productfoundry.akka.cqrs

import play.api.libs.json._

case class DomainCommit[+E <: AggregateEvent](revision: DomainRevision,
                                              commit: Commit[E]) extends Persistable

object DomainCommit {

  import play.api.libs.functional.syntax._

  implicit def DomainCommitFormat[E <: AggregateEvent : Format]: Format[DomainCommit[E]] = (
    (__ \ "revision").format[DomainRevision] and
      (__ \ "commit").format[Commit[E]]
    )(DomainCommit.apply[E], c => DomainCommit.unapply(c).get)
}