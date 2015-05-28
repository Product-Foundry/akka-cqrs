package com.productfoundry.akka.cqrs

/**
 * A successful commit to the aggregate.
 *
 * @param revision of the aggregate to which the commit was applied.
 * @param events with state changes.
 * @param timestamp the commit was created.
 * @param headers with commit info.
 */
case class Commit(revision: AggregateRevision,
                  events: Seq[AggregateEvent],
                  timestamp: Long = System.currentTimeMillis(),
                  headers: Map[String, String] = Map.empty) extends Persistable
