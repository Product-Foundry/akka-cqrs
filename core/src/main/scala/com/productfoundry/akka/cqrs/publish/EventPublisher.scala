package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.messaging.MessagePublisher

/**
 * Commit handler that publishes all events in the commit
 */
trait EventPublisher extends CommitHandler with MessagePublisher[EventPublication] {
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
      publishMessage(EventPublication(record).includeCommander(sender()))
    }
  }
}
