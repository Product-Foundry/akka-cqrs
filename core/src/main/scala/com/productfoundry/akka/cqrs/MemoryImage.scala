package com.productfoundry.akka.cqrs

import akka.actor.{ReceiveTimeout, ActorLogging, ActorRefFactory, Props}
import akka.persistence._

import scala.concurrent.stm.{Ref, _}
import scala.concurrent.duration._

object MemoryImage {
  def apply[State <: Projection[State], Event <: AggregateEvent](actorRefFactory: ActorRefFactory, persistenceId: String)(initialState: State) = {
    new MemoryImage(actorRefFactory, persistenceId)(initialState)
  }

  object RecoveryStatus extends Enumeration {
    type RecoveryStatus = Value
    val Unstarted, Started, Completed = Value
  }
}

/**
 * Tracks an aggregator projection. and uses the provided `initialState` and `update` to project the
 * committed events onto the current state.
 */
class MemoryImage[State <: Projection[State], -Event <: AggregateEvent] private (actorRefFactory: ActorRefFactory, persistenceId: String)(initialState: State) extends ProjectionProvider[State] {
  import MemoryImage.RecoveryStatus
  
  private val recoveryStatus: Ref[RecoveryStatus.RecoveryStatus] = Ref(RecoveryStatus.Unstarted)
  private val state: Ref[State] = Ref(initialState)
  private val revision: Ref[DomainRevision] = Ref(DomainRevision.Initial)
  private val ref = actorRefFactory.actorOf(Props(new MemoryImageActor(persistenceId)))

  awaitRecover()

  /**
   * Blocks until initial recovery of the memory image is complete.
   */
  private def awaitRecover(): Unit = {
    atomic { implicit txn =>
      if (recoveryStatus() != RecoveryStatus.Completed) {
        retry
      }
    }
  }

  override def get: State = state.single.get

  override def getWithRevision(minimum: DomainRevision): (State, DomainRevision) = {
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
   * Applies the given commit to the memory image.
   * @param domainCommit to apply.
   */
  def update(domainCommit: DomainCommit[Event]): Unit = {
    atomic { implicit txn =>
      val commit = domainCommit.commit
      val aggregateRevision = commit.revision

      commit.events.foreach { event =>
        state.transform(_.project(aggregateRevision)(event))
      }

      revision() = domainCommit.revision
    }
  }

  class MemoryImageActor(val persistenceId: String) extends PersistentView with ActorLogging {
    override val viewId: String = s"$persistenceId-view"

    override def preStart(): Unit = {
      recoveryStatus.single.update(RecoveryStatus.Started)
      context.setReceiveTimeout(5.seconds)

      super.preStart()
    }

    override def receive: Receive = {
      case commit: DomainCommit[Event] =>
        update(commit)

      case ReceiveTimeout =>
        log.info("Assuming recovery is complete due to receive timeout: {}", persistenceId)
        recoveryStatus.single.update(RecoveryStatus.Completed)
        context.setReceiveTimeout(Duration.Undefined)
    }
  }
}
