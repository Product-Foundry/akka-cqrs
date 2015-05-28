package com.productfoundry.akka.cqrs.project.domain

import akka.actor.{ActorLogging, ActorRefFactory, Props, ReceiveTimeout}
import akka.persistence._
import com.productfoundry.akka.cqrs.project.Projection

import scala.concurrent.duration._
import scala.concurrent.stm.{Ref, _}

object DomainAggregatorView {
  def apply[P <: Projection[P]](actorRefFactory: ActorRefFactory, persistenceId: String)(initialState: P)(recoveryThreshold: FiniteDuration = 5.seconds) = {
    new DomainAggregatorView(actorRefFactory, persistenceId)(initialState)(recoveryThreshold)
  }

  object RecoveryStatus extends Enumeration {
    type RecoveryStatus = Value
    val Idle, Recovering, Done = Value
  }
}

/**
 * Projects domain commits.
 */
class DomainAggregatorView[P <: Projection[P]] private (actorRefFactory: ActorRefFactory, persistenceId: String)(initial: P)(recoveryThreshold: FiniteDuration) extends DomainProjectionProvider[P] {

  import DomainAggregatorView.RecoveryStatus
  
  private val recoveryStatus: Ref[RecoveryStatus.RecoveryStatus] = Ref(RecoveryStatus.Idle)
  private val state: Ref[P] = Ref(initial)
  private val revision: Ref[DomainRevision] = Ref(DomainRevision.Initial)
  private val ref = actorRefFactory.actorOf(Props(new DomainView(persistenceId)))

  awaitRecover()

  /**
   * Blocks until initial recovery is complete.
   */
  private def awaitRecover(): Unit = {
    atomic { implicit txn =>
      if (recoveryStatus() != RecoveryStatus.Done) {
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
      state.transform(_.project(domainCommit.commit))
      revision() = domainCommit.revision
    }
  }

  class DomainView(val persistenceId: String) extends PersistentView with ActorLogging {
    override val viewId: String = s"$persistenceId-view"

    override def preStart(): Unit = {
      recoveryStatus.single.update(RecoveryStatus.Recovering)
      context.setReceiveTimeout(recoveryThreshold)

      super.preStart()
    }

    override def receive: Receive = {
      case commit: DomainCommit =>
        project(commit)

      case ReceiveTimeout =>
        log.info("Assuming recovery is complete due to receive timeout: {}", persistenceId)
        recoveryStatus.single.update(RecoveryStatus.Done)
        context.setReceiveTimeout(Duration.Undefined)
    }
  }
}
