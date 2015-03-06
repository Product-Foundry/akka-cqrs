package com.productfoundry.akka.cqrs

case class DomainCommit[+E <: DomainEvent](revision: DomainRevision,
                                           timestamp: Long,
                                           commit: Commit[E])

object DomainCommit {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit def DomainFormat[E <: DomainEvent : Format]: Format[DomainCommit[E]] = (
    (__ \ "revision").format[DomainRevision] and
      (__ \ "timestamp").format[Long] and
      (__ \ "commit").format[Commit[E]]
    )(DomainCommit.apply[E] _, c => DomainCommit.unapply(c).get)
}
