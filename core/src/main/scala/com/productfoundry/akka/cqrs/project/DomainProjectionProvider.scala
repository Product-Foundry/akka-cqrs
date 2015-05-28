package com.productfoundry.akka.cqrs.project

import akka.actor.{ActorLogging, ActorRefFactory, Props, ReceiveTimeout}
import akka.persistence._

import scala.concurrent.duration._
import scala.concurrent.stm.{Ref, _}

object DomainProjectionProvider {
  def apply[P <: Projection[P]](actorRefFactory: ActorRefFactory, persistenceId: String)(initialState: P)(recoveryThreshold: FiniteDuration = 5.seconds) = {
    new DomainProjectionProvider(actorRefFactory, persistenceId)(initialState)(recoveryThreshold)
  }

  object RecoveryStatus extends Enumeration {
    type RecoveryStatus = Value
    val Unstarted, Started, Completed = Value
  }
}

/**
 * Projects domain commits.
 */
class DomainProjectionProvider[P <: Projection[P]] private (actorRefFactory: ActorRefFactory, persistenceId: String)(initial: P)(recoveryThreshold: FiniteDuration) extends ProjectionProvider[P] {

  import DomainProjectionProvider.RecoveryStatus
  
  private val recoveryStatus: Ref[RecoveryStatus.RecoveryStatus] = Ref(RecoveryStatus.Unstarted)
  private val state: Ref[P] = Ref(initial)
  private val revision: Ref[DomainRevision] = Ref(DomainRevision.Initial)
  private val ref = actorRefFactory.actorOf(Props(new DomainView(persistenceId)))

  awaitRecover()

  /**
   * Blocks until initial recovery is complete.
   */
  private def awaitRecover(): Unit = {
    atomic { implicit txn =>
      if (recoveryStatus() != RecoveryStatus.Completed) {
        retry
      }
    }
  }

  override def get: P = state.single.get

  override def getWithRevision(minimum: DomainRevision): (P, DomainRevision) = {
    ref ! Update(replayMax = minimum.value)

    atomic { implicit txn =>
      if (revision() < minimum) {
        retry
      }
      else {
        (state(), revision())
      }
    }
  }

  /**
   * Projects the given commit.
   * @param domainCommit to apply.
   */
  def project(domainCommit: DomainCommit): Unit = {
    atomic { implicit txn =>
      val commit = domainCommit.commit
      val headers = CommitHeaders(domainCommit.revision, commit.revision, commit.timestamp, commit.headers)
      state.transform(_.project(headers, commit.events))
      revision() = domainCommit.revision
    }
  }

  class DomainView(val persistenceId: String) extends PersistentView with ActorLogging {
    override val viewId: String = s"$persistenceId-view"

    override def preStart(): Unit = {
      recoveryStatus.single.update(RecoveryStatus.Started)
      context.setReceiveTimeout(recoveryThreshold)

      super.preStart()
    }

    override def receive: Receive = {
      case commit: DomainCommit =>
        project(commit)

      case ReceiveTimeout =>
        log.info("Assuming recovery is complete due to receive timeout: {}", persistenceId)
        recoveryStatus.single.update(RecoveryStatus.Completed)
        context.setReceiveTimeout(Duration.Undefined)
    }
  }
}
