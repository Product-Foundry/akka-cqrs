package com.productfoundry.akka.cqrs

/**
 * A successful commit to the aggregate.
 *
 * @param revision of the entity to which the commit was applied.
 * @param events change state.
 * @param headers with commit info.
 * @tparam E Type of the events in the commit.
 */
case class Commit[+E <: AggregateEvent](revision: AggregateRevision,
                                        events: Seq[E],
                                        timestamp: Long,
                                        headers: Map[String, String] = Map.empty) extends Persistable
