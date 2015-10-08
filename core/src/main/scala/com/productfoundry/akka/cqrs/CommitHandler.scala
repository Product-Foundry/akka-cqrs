package com.productfoundry.akka.cqrs

/**
 * Mixin for aggregates to handle persisted commits.
 */
trait CommitHandler {
  this: Aggregate =>

  /**
   * Handle a persisted commit.
   * @param commit to handle.
   * @param response which can be manipulated by additional commit handlers.
   * @return Updated response.
   */
  def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse
}
