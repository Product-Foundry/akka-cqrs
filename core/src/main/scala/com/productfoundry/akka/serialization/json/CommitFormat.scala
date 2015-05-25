package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs.{AggregateEvent, AggregateRevision, Commit}
import play.api.libs.json._

trait CommitFormat {

  import play.api.libs.functional.syntax._

  implicit def CommitFormat[E <: AggregateEvent : Format]: Format[Commit[E]] = (
    (__ \ "revision").format[AggregateRevision] and
      (__ \ "events").format[Seq[E]] and
      (__ \ "timestamp").format[Long] and
      (__ \ "headers").format[Map[String, String]]
    )(Commit.apply[E], c => Commit.unapply(c).get)
}

object CommitFormat extends CommitFormat