package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs.Aggregate

/**
 * Mixin for actors to publish all commit messages onto the system event stream.
 */
trait LocalCommitPublisher extends CommitPublisher {
  this: Aggregate =>

  /**
   * Publish directly to the system event stream.
   * @param publication to publish.
   */
  override def publishCommit(publication: Publication): Unit = {
    context.system.eventStream.publish(publication)
  }
}