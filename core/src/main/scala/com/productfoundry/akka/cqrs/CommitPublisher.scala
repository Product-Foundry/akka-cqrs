package com.productfoundry.akka.cqrs

/**
 * Publishes persisted commits.
 */
trait CommitPublisher extends CommitHandler {
  this: Aggregate[_] =>

  override abstract def handleCommit(commit: Commit[AggregateEvent]): Unit = {
    publishCommit(CommitPublication(commit).includeCommander(sender()))
    super.handleCommit(commit)
  }

  /**
   * Publish a persisted commit.
   *
   * @param commitPublication to publish.
   */
  def publishCommit(commitPublication: CommitPublication[AggregateEvent]): Unit
}
