package com.productfoundry.akka.cqrs

/**
 * Mixin for actors to publish all commit messages onto the system event stream.
 */
trait LocalCommitPublisher extends CommitPublisher {
  this: Aggregate[_] =>

  override def publishCommit(commitPublication: CommitPublication[AggregateEvent]): Unit = {
    context.system.eventStream.publish(commitPublication)
  }
}