package com.productfoundry.akka.cqrs.project.domain

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

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
@deprecated("use Persistence Query instead", "0.1.28")
class DomainAggregator(override val persistenceId: String, val snapshotInterval: Int = 100)
  extends PersistentActor
  with ActorLogging
  with ReceivePipeline
  with EventPublicationInterceptor{

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
  override def receiveCommand: Receive = {

    case eventRecord: AggregateEventRecord =>
      persist(DomainCommit(revision.next, eventRecord)) { commit =>
        revision = commit.revision

        sender() ! revision

        if (revision.value % snapshotInterval == 0) {
          saveSnapshot(DomainAggregatorSnapshot(revision))
        }
      }
  }

  /**
   * Recover domain revision
   */
  override def receiveRecover: Receive = {

    case commit: DomainCommit =>
      log.debug("Recovered: {}", commit)
      revision = commit.revision

    case SnapshotOffer(_, snapshot: DomainAggregatorSnapshot) =>
      log.debug("Recovered revision from snapshot: {}", snapshot)
      revision = snapshot.revision
  }
}
