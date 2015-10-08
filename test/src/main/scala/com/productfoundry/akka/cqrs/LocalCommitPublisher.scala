package com.productfoundry.akka.cqrs

/**
 * Mix in with aggregates to publishes generated commits for testing purposes.
 *
 * Makes it easy to see what happened in an aggregate in combination with the [[LocalCommitCollector]].
 */
trait LocalCommitPublisher extends CommitHandler {
  this: Aggregate =>


  /**
   * Can be overridden by commit handlers mixins to add additional commit behavior.
   * @param commit to handle.
   * @param response which can be manipulated by additional commit handlers.
   * @return Updated response.
   */
  override def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {
    context.system.eventStream.publish(commit)
    response
  }
}
