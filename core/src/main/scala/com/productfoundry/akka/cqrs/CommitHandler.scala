package com.productfoundry.akka.cqrs

/**
 * Mixin for aggregates to handle persisted commits.
 */
trait CommitHandler {

  /**
   * Handle a persisted commit.
   * @param commit that was persisted.
   */
  def handleCommit(commit: Commit[AggregateEvent]): Unit
}
