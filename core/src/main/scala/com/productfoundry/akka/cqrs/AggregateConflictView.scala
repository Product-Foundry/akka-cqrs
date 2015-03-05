package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, PoisonPill, ReceiveTimeout}
import akka.persistence.{PersistentView, Recover}

import scala.concurrent.duration._

class AggregateConflictView(override val persistenceId: String, val originalSender: ActorRef, val conflict: RevisionConflict)
  extends PersistentView {

  override val viewId: String = s"$persistenceId-conflict"

  var commits: Vector[Commit[DomainEvent]] = Vector.empty

  override def preStart(): Unit = {
    // TODO [AK] Make timeout configurable?
    context.setReceiveTimeout(30.seconds)
    self ! Recover(toSequenceNr = conflict.actual.value)
  }

  override def receive: Receive = {
    case commit: Commit[DomainEvent] =>
      if (commit.revision >= conflict.expected) {
        commits = commits :+ commit
      }

      if (AggregateRevision(lastSequenceNr) == conflict.actual) {
        self ! PoisonPill
      }

    case ReceiveTimeout =>
      self ! PoisonPill
  }

  override def postStop(): Unit = {
    originalSender ! AggregateStatus.Failure(conflict.copy(commits = commits.toSeq))
  }
}
