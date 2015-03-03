package com.productfoundry.akka.cqrs

/**
 * A successful commit to the aggregate.
 * @param id of the entity to which the commit was applied.
 * @param revision of the entity to which the commit was applied.
 * @param timestamp of the commit.
 * @param events change state.
 * @param headers with commit info.
 * @tparam E Type of the events in the commit.
 */
case class Commit[+E <: DomainEvent](id: EntityId,
                                     revision: AggregateRevision,
                                     timestamp: Long,
                                     events: Seq[E],
                                     headers: Map[String, String] = Map.empty)
