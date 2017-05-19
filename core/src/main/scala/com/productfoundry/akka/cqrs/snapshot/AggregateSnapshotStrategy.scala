package com.productfoundry.akka.cqrs.snapshot

import akka.actor.Cancellable
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.productfoundry.akka.cqrs._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait AggregateSnapshotStrategy
  extends PersistentActor
    with CommitHandler {

  this: Aggregate with AggregateSnapshotSupport =>

  implicit val executionContext: ExecutionContext = context.dispatcher

  def getStateSnapshot: Option[AggregateStateSnapshot]

  def snapshotInterval: Int = AggregateSnapshotStrategy.defaultInternal

  def snapshotDelay: FiniteDuration = AggregateSnapshotStrategy.defaultDelay

  abstract override def receiveCommand: Receive = receiveSnapshotStrategyCommand orElse super.receiveCommand

  private def receiveSnapshotStrategyCommand: Receive = {

    case AggregateSnapshotStrategy.CreateSnapshot =>
      log.info("Creating snapshot")
      saveSnapshot(getStateSnapshot)
      lastSnapshotRevision = revision
      cancellableSnapshotOption = None
  }

  abstract override def receiveRecover: Receive = receiveSnapshotStrategyRecover orElse super.receiveRecover

  private var lastSnapshotRevision: AggregateRevision = AggregateRevision.Initial
  private var cancellableSnapshotOption: Option[Cancellable] = None

  private def receiveSnapshotStrategyRecover: Receive = {

    case snapshotOffer@SnapshotOffer(_, aggregateSnapshot: AggregateSnapshot) =>
      lastSnapshotRevision = aggregateSnapshot.revision
      super.receiveRecover(snapshotOffer)
  }

  override abstract def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {

    // Check if we need to create a snapshot
    if (revision.value - lastSnapshotRevision.value >= snapshotInterval) {

      // If we've already planned a snapshot we want to delay it starting this new commit
      cancellableSnapshotOption.foreach { cancellableSnapshot =>
        log.debug("Delay planned snapshot")
        cancellableSnapshot.cancel()
      }

      // Schedule snapshot creation
      cancellableSnapshotOption = Some(
        context.system.scheduler.scheduleOnce(snapshotDelay, self, AggregateSnapshotStrategy.CreateSnapshot)
      )
    }

    // Invoke other handlers
    super.handleCommit(commit, response)
  }
}

object AggregateSnapshotStrategy {

  val defaultInternal: Int = 1000

  val defaultDelay: FiniteDuration = 10.seconds

  case object CreateSnapshot

}