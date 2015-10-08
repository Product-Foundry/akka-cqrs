package com.productfoundry.akka.cqrs.project.domain

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryFailure, SnapshotOffer}
import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.{ProjectionRevision, ProjectionUpdate, Projector}

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
  with Projector
  with ActorLogging {

  /**
   * Uniquely identifies a projection created by the projector.
   */
  override def projectionId: String = persistenceId

  /**
   * Keeps track of the current revision.
   */
  private var revision = ProjectionRevision.Initial

  /**
   * @return the current projection revision of the domain aggregator.
   */
  def currentRevision = revision

  /**
   * Receive published events.
   */
  override def receiveCommand: Receive = receivePublishedEvent

  /**
   * Handle received event
   */
  override def project: ReceiveEventRecord = {
    case eventRecord: AggregateEventRecord =>
      persist(DomainCommit(revision.next, eventRecord)) { commit =>
        updateState(commit)

        handleProjectedUpdate(ProjectionUpdate(projectionId, commit.revision, eventRecord.tag))

        // TODO [AK] This fails when Akka does not know how to serialize revision
        if (revision.value % snapshotInterval == 0) {
          saveSnapshot(revision)
        }
      }
  }

  private def updateState(commit: DomainCommit): Unit = {
    revision = commit.revision
  }

  /**
   * Recover domain revision
   */
  override def receiveRecover: Receive = {

    case RecoveryFailure(cause) =>
      log.error(cause, "Unable to recover: {}", persistenceId)

    case commit: DomainCommit =>
      log.debug("Recovered: {}", commit)
      updateState(commit)

    case SnapshotOffer(_, snapshot: ProjectionRevision) =>
      log.debug("Recovered revision from snapshot: {}", snapshot)
      revision = snapshot
  }

  /**
   * Handle a projected update.
   * @param update to handle.
   */
  override def handleProjectedUpdate(update: ProjectionUpdate): Unit = {
    sender() ! update.revision
    super.handleProjectedUpdate(update)
  }
}
