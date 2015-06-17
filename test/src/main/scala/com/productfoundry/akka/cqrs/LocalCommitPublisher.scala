package com.productfoundry.akka.cqrs

/**
 * Mix in with aggregates to publishes generated commits for testing purposes.
 *
 * Makes it easy to see what happened in an aggregate in combination with the [[LocalCommitCollector]].
 */
trait LocalCommitPublisher extends CommitHandler {
  this: Aggregate =>

  override def handleCommit(commit: Commit): Unit = {
    context.system.eventStream.publish(commit)
  }
}
