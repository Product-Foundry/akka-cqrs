package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs._

/**
 * Commit handler that publishes commits.
 */
trait CommitPublisher extends CommitHandler {
  this: Aggregate[_] =>

  /**
   * Creates a publication from the commit, including the commander, which can be used for sending additional info.
   * @param commit to handle.
   */
  override abstract def handleCommit(commit: Commit[AggregateEvent]): Unit = {
    publishCommit(Publication(commit).includeCommander(sender()))
    super.handleCommit(commit)
  }

  /**
   * Publish a persisted commit.
   * @param commitPublication to publish.
   */
  def publishCommit(commitPublication: Publication[AggregateEvent]): Unit
}
