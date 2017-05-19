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
   * @return optional headers to store with the commit
   */
  def headersOption: Option[CommitHeaders]

  /**
   * @return payload for additional response.
   */
  def response: Option[Any]

  /**
   * Sets aggregate response payload.
   *
   * @param response to set.
   * @return updated payload.
   */
  def withResponse(response: Any): Changes

  /**
    * Specifies headers to store.
    *
    * @param headers to store.
    * @return changes with updated headers.
    */
  def withHeaders(headers: CommitHeaders): Changes

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

private[this] case class AggregateChanges(events: Seq[AggregateEvent], response: Option[Any]= None, headersOption: Option[CommitHeaders] = None) extends Changes {

  /**
   * @return True if there are no changes.
   */
  override def isEmpty: Boolean = events.isEmpty

  /**
   * Sets aggregate response payload.
   *
   * @param response to set.
   * @return changes with updated payload.
   */
  override def withResponse(response: Any): AggregateChanges = copy(response = Some(response))

  /**
   * Specifies headers to store.
   *
   * @param headers to store.
   * @return changes with updated headers.
   */
  override def withHeaders(headers: CommitHeaders): AggregateChanges = copy(headersOption = Some(headers))

  /**
   * Creates a commit from the specified changes.
   * @param tag to base the commit on.
   * @return commit to store.
   */
  override def createCommit(tag: AggregateTag): Commit = {
    val entries = events.zip(tag.revision.upcoming).map { case (event, expectedRevision) =>
      CommitEntry(expectedRevision, event)
    }

    Commit(tag, headersOption, entries)
  }
}
