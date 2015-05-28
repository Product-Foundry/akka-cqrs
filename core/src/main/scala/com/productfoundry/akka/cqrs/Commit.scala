package com.productfoundry.akka.cqrs

/**
 * A successful commit to the aggregate.
 *
 * @param metadata about the commit.
 * @param events describing all aggregate changes.
 */
case class Commit(metadata: CommitMetadata, events: Seq[AggregateEvent]) extends Persistable with AggregateEventContainer {

  /**
   * @return unique commit id.
   */
  def id = metadata.persistenceId

  /**
   * @return revision of the commit.
   */
  def revision: AggregateRevision = metadata.revision

  /**
   * @return timestamp when the commit was created.
   */
  def timestamp: Long = metadata.timestamp

  /**
   * @return additional commit information.
   */
  def headers: Map[String, String] = metadata.headers
}
