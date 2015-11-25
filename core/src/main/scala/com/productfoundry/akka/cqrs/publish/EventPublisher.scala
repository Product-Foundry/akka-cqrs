package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs._

/**
 * Commit handler that publishes all events in the commit
 */
trait EventPublisher extends CommitHandler  {
  this: Aggregate =>

  /**
   * Handle a persisted commit.
   * @param commit to handle.
   * @param response which can be manipulated by additional commit handlers.
   * @return Updated response.
   */
  override abstract def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {
    publishCommit(commit)
    super.handleCommit(commit, response)
  }

  /**
   * Creates event publications from the commit.
   *
   * All publications include the commander, which can be used for sending additional info.
   * @param commit containing all events to publish.
   */
  def publishCommit(commit: Commit): Unit = {
    commit.records.foreach { record =>
      publishEvent(EventPublication(record))
    }
  }

  /**
    * Handles publication.
    * @param eventPublication to publish.
    */
  def publishEvent(eventPublication: EventPublication): Unit
}
