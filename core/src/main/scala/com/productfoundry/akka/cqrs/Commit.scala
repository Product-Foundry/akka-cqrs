package com.productfoundry.akka.cqrs


/**
 * A successful commit to the aggregate.
 * @param revision of the entity to which the commit was applied.
 * @param timestamp of the commit.
 * @param events change state.
 * @param headers with commit info.
 * @tparam E Type of the events in the commit.
 */
case class Commit[+E <: AggregateEvent](revision: AggregateRevision,
                                     timestamp: Long,
                                     events: Seq[E],
                                     headers: Map[String, String] = Map.empty)


object Commit {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit def CommitFormat[E <: AggregateEvent : Format]: Format[Commit[E]] = (
    (__ \ "revision").format[AggregateRevision] and
      (__ \ "timestamp").format[Long] and
      (__ \ "events").format[Seq[E]] and
      (__ \ "headers").format[Map[String, String]]
    )(Commit.apply[E] _, c => Commit.unapply(c).get)
}