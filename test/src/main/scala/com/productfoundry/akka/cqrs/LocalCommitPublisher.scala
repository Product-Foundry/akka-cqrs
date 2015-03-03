package com.productfoundry.akka.cqrs

import akka.actor.Actor

/**
 * Mixin for actors to publish all commit messages onto the system event stream.
 */
trait LocalCommitPublisher extends CommitPublisher {
  this: Actor =>

  override def publishCommit(commit: Commit[DomainEvent]): Unit = {
    context.system.eventStream.publish(commit)
  }
}