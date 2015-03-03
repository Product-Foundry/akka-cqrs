package com.productfoundry.akka.cqrs

import akka.persistence.PersistentActor

/**
 * Persistent actor that aggregates all received commits.
 *
 * Simplifies building projections.
 *
 * Having a single aggregator for all commits is not a good idea.
 *
 *  - All commits are stored twice, once in the aggregate and once in the aggregator.
 *  - Storing commits twice means logs can get out of sync; preferably the aggregate is leading.
 *  - Commit aggregator becomes a bottleneck because it needs to handle all commits in the system.
 *  - Even worse when clustering.
 *
 * Unfortunately there is no better journal-independent solution for rebuilding projections right now.
 */
class GlobalAggregator extends PersistentActor {

  /**
   * Persistence id is based on the actor path.
   */
  override val persistenceId: String = PersistenceId(self.path).value

  /**
   * @return the current revision of this aggregate.
   */
  def revision = GlobalRevision(lastSequenceNr)

  /**
   * Simply persists all received commits.
   */
  override def receiveCommand: Receive = {
    case commit: Commit[DomainEvent] =>
      persist(GlobalCommit(revision.next, System.currentTimeMillis(), commit)) { globalCommit =>
        sender() ! globalCommit.revision
      }
  }

  /**
   * Nothing to recover, projections are created using views.
   */
  override def receiveRecover: Receive = {
    case _ =>
  }
}

object GlobalAggregator {
  case object Get
}