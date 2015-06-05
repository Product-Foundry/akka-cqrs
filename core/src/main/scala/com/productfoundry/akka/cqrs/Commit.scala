package com.productfoundry.akka.cqrs

/**
 * A commit with aggregate event records.
 *
 * @param snapshot at the time of the commit.
 * @param headers with additional commit info.
 * @param timestamp of the commit.
 * @param events in the commit.
 */
case class Commit(snapshot: AggregateSnapshot,
                  headers: Map[String, String] = Map.empty,
                  timestamp: Long = System.currentTimeMillis(),
                  events: Seq[AggregateEventRecord]) extends Persistable