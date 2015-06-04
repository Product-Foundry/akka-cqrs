package com.productfoundry.akka.cqrs

/**
 * Describes an aggregate commit.
 *
 * @param aggregateId of the aggregate.
 * @param revision of the commit.
 * @param headers containing additional commit info.
 * @param timestamp when the commit was created.
 */
case class CommitMetadata(aggregateId: String,
                          revision: AggregateRevision,
                          headers: Map[String, String] = Map.empty,
                          timestamp: Long = System.currentTimeMillis()) {

  /**
   * Uniquely identifies a commit.
   */
  val id = s"$aggregateId-$revision"
}