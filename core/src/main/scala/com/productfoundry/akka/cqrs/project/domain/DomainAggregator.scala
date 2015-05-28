package com.productfoundry.akka.cqrs.project.domain

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryFailure, SnapshotOffer}
import com.productfoundry.akka.cqrs.Commit
import com.productfoundry.akka.cqrs.project.domain.DomainAggregator._

/**
 * Persistent actor that aggregates all received commits.
 *
 * Simplifies rebuilding projections.
 *
 * There are some drawbacks to this as well:
 *
 * - All commits are stored twice, once in the aggregate and once in the aggregator.
 * - Storing commits twice means logs can get out of sync; however the domain aggregator is a projection in itself and eventually consistent.
 * - Commit aggregator can become a bottleneck since the updates all need to go a single actor.
 *
 * Unfortunately there is no better journal-independent solution for rebuilding projections right now. At some point
 * it makes sense to use something like Akka streams for this.
 *
 * @param persistenceId used for persisting all received events.
 * @param snapshotInterval defines how often a snapshot is created, defaults to snapshot after every 100 aggregated commits.
 */
class DomainAggregator(override val persistenceId: String, val snapshotInterval: Int = 100)
  extends PersistentActor
  with ActorLogging {

  /**
   * Keeps track of the current revision.
   *
   * The revision should increment with every aggregated commit without creating gaps.
   * Not backed by [[lastSequenceNr]], because mixins can also persist events for internal use, which shouldn't
   * affect the aggregator revision.
   */
  private var revision = DomainRevision.Initial

  /**
   * @return the current revision of this aggregator.
   */
  def currentRevision = revision

  /**
   * Persist all commits.
   */
  override def receiveCommand: Receive = {
    case commit: Commit => aggregateCommit(commit)
  }

  /**
   * Persists the commit and notifies the sender of the domain revision.
   * @param commit to persist.
   */
  def aggregateCommit(commit: Commit): Unit = {
    persist(DomainCommit(revision.next, commit)) { domainCommit =>
      updateState(domainCommit)

      sender() ! DomainAggregatorRevision(revision)

      if (revision.value % snapshotInterval == 0) {
        saveSnapshot(revision)
      }
    }
  }

  private def updateState(domainCommit: DomainCommit): Unit = {
    revision = domainCommit.revision
  }

  /**
   * Recover domain revision
   */
  override def receiveRecover: Receive = {

    case RecoveryFailure(cause) =>
      log.error(cause, "Unable to recover: {}", persistenceId)

    case domainCommit: DomainCommit =>
      log.debug("Recovered: {}", domainCommit)
      updateState(domainCommit)

    case SnapshotOffer(_, snapshot: DomainRevision) =>
      log.debug("Recovered revision from snapshot: {}", snapshot)
      revision = snapshot
  }
}

object DomainAggregator {

  case class DomainAggregatorRevision(revision: DomainRevision)

}