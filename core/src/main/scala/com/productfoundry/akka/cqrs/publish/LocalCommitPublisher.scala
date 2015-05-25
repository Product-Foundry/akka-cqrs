package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs.{AggregateEvent, Aggregate}

/**
 * Mixin for actors to publish all commit messages onto the system event stream.
 */
trait LocalCommitPublisher extends CommitPublisher {
  this: Aggregate[_] =>

  /**
   * Publish directly to the system event stream.
   * @param commitPublication to publish.
   */
  override def publishCommit(commitPublication: Publication[AggregateEvent]): Unit = {
    context.system.eventStream.publish(commitPublication)
  }
}