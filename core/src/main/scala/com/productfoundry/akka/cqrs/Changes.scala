package com.productfoundry.akka.cqrs

/**
 * Represents the changes that can be committed atomically to the aggregate.
 */
sealed trait Changes {

  /**
   * @return True if there are no changes.
   */
  def isEmpty: Boolean

  /**
   * @return changes to apply the aggregate state.
   */
  def events: Seq[AggregateEvent]

  /**
   * @return additional commit info
   */
  def metadata: Map[String, String]

  /**
   * @return payload for additional response.
   */
  def response: Any

  /**
   * Sets aggregate response payload.
   *
   * @param response to set.
   * @return updated payload.
   */
  def withResponse(response: Any): Changes

  /**
   * Adds additional metadata.
   *
   * @param metadata to add.
   * @return updated metadata.
   */
  def withMetadata(metadata: (String, String)*): Changes

  /**
   * Creates a commit from the specified changes.
   * @param tag to base the commit on.
   * @return created commit.
   */
  def createCommit(tag: AggregateTag): Commit
}

/**
 * Changes companion.
 */
object Changes {

  /**
   * Create changes.
   * @param events changes to apply the aggregate state.
   * @return changes.
   */
  def apply(events: AggregateEvent*): Changes = AggregateChanges(events)
}

private[this] case class AggregateChanges(events: Seq[AggregateEvent], response: Any = Unit, metadata: Map[String, String] = Map.empty) extends Changes {

  /**
   * @return True if there are no changes.
   */
  override def isEmpty: Boolean = events.isEmpty

  /**
   * Sets aggregate response payload.
   *
   * @param response to set.
   * @return updated payload.
   */
  override def withResponse(response: Any) = copy(response = response)

  /**
   * Adds additional metadata.
   *
   * @param metadata to add.
   * @return updated metadata.
   */
  override def withMetadata(metadata: (String, String)*) = copy(metadata = this.metadata ++ metadata)

  /**
   * Creates a commit from the specified changes.
   * @param tag to base the commit on.
   * @return created commit.
   */
  override def createCommit(tag: AggregateTag): Commit = {
    val headers = AggregateEventHeaders(metadata, System.currentTimeMillis())
    val entries = events.zip(tag.revision.upcoming).map { case (event, expectedRevision) =>
      CommitEntry(expectedRevision, event)
    }

    Commit(tag, headers, entries)
  }
}
