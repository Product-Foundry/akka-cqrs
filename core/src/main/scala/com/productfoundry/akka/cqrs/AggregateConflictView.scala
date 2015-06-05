package com.productfoundry.akka.cqrs

import akka.actor.{ActorLogging, ActorRef, PoisonPill, ReceiveTimeout}
import akka.persistence.{RecoveryFailure, PersistentView, Recover}

import scala.concurrent.duration._

class AggregateConflictView(override val persistenceId: String, val originalSender: ActorRef, val conflict: RevisionConflict)
  extends PersistentView
  with ActorLogging {

  override val viewId: String = s"$persistenceId-conflict"

  var commits: Vector[Commit] = Vector.empty

  override def preStart(): Unit = {
    context.setReceiveTimeout(5.seconds)
    self ! Recover(toSequenceNr = conflict.actual.value)
  }

  override def receive: Receive = {
    case commit: Commit =>
      if (commit.headers.snapshot.revision > conflict.expected) {
        commits = commits :+ commit
      }

      if (AggregateRevision(lastSequenceNr) == conflict.actual) {
        originalSender ! AggregateResult.Failure(conflict.copy(commits = commits.toSeq))
        self ! PoisonPill
      }

    case ReceiveTimeout =>
      originalSender ! AggregateResult.Failure(conflict)
      self ! PoisonPill


    case RecoveryFailure(cause) =>
      log.error(cause, "Unable to get conflict info for {}", persistenceId)
      originalSender ! AggregateResult.Failure(conflict)
      self ! PoisonPill
  }
}
