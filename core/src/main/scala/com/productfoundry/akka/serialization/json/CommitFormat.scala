package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.cqrs.{AggregateEvent, Commit}
import play.api.libs.json._

trait CommitFormat {
  implicit def CommitFormat(implicit eventFormat: Format[AggregateEvent]): Format[Commit] = Json.format[Commit]
}

object CommitFormat extends CommitFormat