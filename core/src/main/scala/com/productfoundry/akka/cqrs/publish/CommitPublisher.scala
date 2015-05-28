package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs._

/**
 * Commit handler that publishes commits.
 */
trait CommitPublisher extends CommitHandler {
  this: Aggregate =>

  /**
   * Creates a publication from the commit, including the commander, which can be used for sending additional info.
   * @param commit to handle.
   */
  override abstract def handleCommit(commit: Commit): Unit = {
    publishCommit(Publication(commit).includeCommander(sender()))
    super.handleCommit(commit)
  }

  /**
   * Publish a persisted commit.
   * @param publication to publish.
   */
  def publishCommit(publication: Publication): Unit
}
