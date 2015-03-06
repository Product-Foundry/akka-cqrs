package com.productfoundry.akka.cqrs

/**
 * Publishes persisted commits.
 */
trait CommitPublisher extends CommitHandler {

  override abstract def handleCommit(commit: Commit[AggregateEvent]): Unit = {
    publishCommit(commit)
    super.handleCommit(commit)
  }

  /**
   * Publish a persisted commit.
   * @param commit that was persisted.
   */
  def publishCommit(commit: Commit[AggregateEvent]): Unit
}
