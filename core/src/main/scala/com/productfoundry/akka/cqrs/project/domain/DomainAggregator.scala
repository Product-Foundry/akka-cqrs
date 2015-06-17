package com.productfoundry.akka.cqrs.project.domain

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryFailure, SnapshotOffer}
import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.domain.DomainAggregator._
import com.productfoundry.akka.cqrs.publish.EventSubscriber

/**
 * Persistent actor that aggregates all received event records.
 *
 * Simplifies rebuilding projections.
 *
 * There are some drawbacks to this as well:
 *
 * - All event records are stored twice, once in the aggregate and once in the aggregator.
 * - Storing event records twice means logs can get out of sync; however the domain aggregator is a projection in itself and eventually consistent.
 * - Aggregator can become a bottleneck since the updates all need to go a single actor.
 *
 * Unfortunately there is no better journal-independent solution for rebuilding projections right now. At some point
 * it makes sense to use something like Akka streams for this.
 *
 * @param persistenceId used for persisting all received events.
 * @param snapshotInterval defines how often a snapshot is created, defaults to snapshot after every 100 aggregated event records.
 */
class DomainAggregator(override val persistenceId: String, val snapshotInterval: Int = 100)
  extends PersistentActor
  with EventSubscriber
  with ActorLogging {

  /**
   * Keeps track of the current revision.
   *
   * The revision should increment with every aggregated event record without creating gaps.
   * Not backed by [[lastSequenceNr]], because mixins can also persist events for internal use, which shouldn't
   * affect the domain revision.
   */
  private var revision = DomainRevision.Initial

  /**
   * @return the current revision of this aggregator.
   */
  def currentRevision = revision

  /**
   * Receive published events.
   */
  override def receiveCommand: Receive = receivePublishedEvent

  /**
   * Handle received event
   */
  override def eventReceived: ReceiveEventRecord = {
    case eventRecord: AggregateEventRecord =>
      persist(DomainCommit(revision.next, eventRecord)) { domainEventRecord =>
        updateState(domainEventRecord)

        sender() ! DomainAggregatorRevision(revision)

        if (revision.value % snapshotInterval == 0) {
          saveSnapshot(revision)
        }
      }
  }

  private def updateState(domainEventRecord: DomainCommit): Unit = {
    revision = domainEventRecord.revision
  }

  /**
   * Recover domain revision
   */
  override def receiveRecover: Receive = {

    case RecoveryFailure(cause) =>
      log.error(cause, "Unable to recover: {}", persistenceId)

    case domainEventRecord: DomainCommit =>
      log.debug("Recovered: {}", domainEventRecord)
      updateState(domainEventRecord)

    case SnapshotOffer(_, snapshot: DomainRevision) =>
      log.debug("Recovered revision from snapshot: {}", snapshot)
      revision = snapshot
  }
}

object DomainAggregator {

  case class DomainAggregatorRevision(revision: DomainRevision)

}