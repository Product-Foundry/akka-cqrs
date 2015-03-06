package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, PoisonPill, ReceiveTimeout}
import akka.persistence.{PersistentView, Recover}

import scala.concurrent.duration._

class AggregateConflictView(override val persistenceId: String, val originalSender: ActorRef, val conflict: RevisionConflict)
  extends PersistentView {

  override val viewId: String = s"$persistenceId-conflict"

  var commits: Vector[Commit[AggregateEvent]] = Vector.empty

  override def preStart(): Unit = {
    context.setReceiveTimeout(5.seconds)
    self ! Recover(toSequenceNr = conflict.actual.value)
  }

  override def receive: Receive = {
    case commit: Commit[AggregateEvent] =>
      if (commit.revision >= conflict.expected) {
        commits = commits :+ commit
      }

      if (AggregateRevision(lastSequenceNr) == conflict.actual) {
        originalSender ! AggregateStatus.Failure(conflict.copy(commits = commits.toSeq))
        self ! PoisonPill
      }

    case ReceiveTimeout =>
      originalSender ! AggregateStatus.Failure(conflict)
      self ! PoisonPill
  }
}
