package com.productfoundry.akka.cqrs

/**
 * Mixin for aggregates to handle persisted commits.
 */
trait CommitHandler {
  this: Aggregate[_] =>

  /**
   * Handle a persisted commit.
   * @param commit to handle.
   */
  def handleCommit(commit: Commit): Unit
}
