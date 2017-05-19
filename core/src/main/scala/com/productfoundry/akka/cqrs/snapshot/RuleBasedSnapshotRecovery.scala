package com.productfoundry.akka.cqrs.snapshot

import akka.actor.Cancellable
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.productfoundry.akka.cqrs.{Aggregate, AggregateRevision}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait RuleBasedSnapshotRecovery
  extends PersistentActor {

  this: Aggregate with AggregateSnapshotRecovery =>

  def getStateSnapshot: Option[AggregateStateSnapshot]

  private var lastSnapshotRevisionInternal: AggregateRevision = AggregateRevision.Initial

  def lastSnapshotRevision: AggregateRevision = lastSnapshotRevisionInternal

  private var cancellableSnapshotOption: Option[Cancellable] = None

  implicit val executionContext: ExecutionContext = context.dispatcher

  abstract override def receiveCommand: Receive = receiveRuleBasedSnapshotRecoveryCommand orElse super.receiveCommand

  private def receiveRuleBasedSnapshotRecoveryCommand: Receive = {

    case SnapshotProtocol.CreateSnapshot =>

      log.info("Creating snapshot")
      saveSnapshot(getStateSnapshot)
      lastSnapshotRevisionInternal = revision
      cancellableSnapshotOption = None

    case SnapshotProtocol.RequestSnapshot(delay) =>

      // If we've already planned a snapshot we want to reschedule based on this request
      cancellableSnapshotOption.foreach { cancellableSnapshot =>
        log.debug("Cancel existing snapshot request")
        cancellableSnapshot.cancel()
      }

      // Schedule snapshot creation
      cancellableSnapshotOption = Some(
        context.system.scheduler.scheduleOnce(delay, self, SnapshotProtocol.CreateSnapshot)
      )
  }

  abstract override def receiveRecover: Receive = receiveRuleBasedSnapshotRecoveryRecover orElse super.receiveRecover

  private def receiveRuleBasedSnapshotRecoveryRecover: Receive = {

    case snapshotOffer@SnapshotOffer(_, aggregateSnapshot: AggregateSnapshot) =>
      lastSnapshotRevisionInternal = aggregateSnapshot.revision
      super.receiveRecover(snapshotOffer)
  }
}

object SnapshotProtocol {

  case object CreateSnapshot

  case class RequestSnapshot(delay: FiniteDuration)

}