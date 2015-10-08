package com.productfoundry.akka.cqrs

import akka.actor.{ActorLogging, ActorRef, PoisonPill, ReceiveTimeout}
import akka.persistence.{RecoveryFailure, PersistentView, Recover}

import scala.concurrent.duration._

/**
 * In case of a conflicting aggregate update, gathers all events between two revisions and send them to the commander.
 * 
 * @param persistenceId of the aggregate.
 * @param commander that send the command to the aggregate.
 * @param conflict that occurred while handling the update.
 */
class AggregateConflictView(override val persistenceId: String, val commander: ActorRef, val conflict: RevisionConflict)
  extends PersistentView
  with ActorLogging {

  /**
   * We're not using snapshots, but the best way to represent it is by including conflict details.
   */
  override val viewId: String = s"$persistenceId-conflict-${conflict.expected}-${conflict.actual}"

  /**
   * Gathers all events that happened between the conflicting revisions.
   */
  private var records: Vector[AggregateEventRecord] = Vector.empty

  override def preStart(): Unit = {
    // Don't let the user wait to long for a response.
    context.setReceiveTimeout(5.seconds)

    super.preStart()
  }

  override def receive: Receive = {
    case commit: Commit =>
      commit.records.foreach { record =>
        val revision = record.tag.revision

        if (revision > conflict.expected && revision <= conflict.actual) {
          records = records :+ record
        }

        if (revision == conflict.actual) {
          commander ! AggregateStatus.Failure(conflict.withRecords(records.toSeq))
          self ! PoisonPill
        }
      }

    case ReceiveTimeout =>
      commander ! AggregateStatus.Failure(conflict)
      self ! PoisonPill

    case RecoveryFailure(cause) =>
      log.error(cause, "Unable to get conflict info for {}", viewId)
      commander ! AggregateStatus.Failure(conflict)
      self ! PoisonPill
  }
}
