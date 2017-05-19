package com.productfoundry.akka.cqrs.snapshot

import akka.persistence._
import com.productfoundry.akka.cqrs.Aggregate
import com.productfoundry.akka.cqrs.publish.EventPublication

import scala.collection.immutable

trait AggregateSnapshotRecovery
  extends PersistentActor {

  this: Aggregate =>

  type SnapshotType <: AggregateStateSnapshot

  def getStateSnapshot(state: S): SnapshotType

  def getStateFromSnapshot(snapshot: SnapshotType): S

  abstract override def receiveCommand: Receive = receiveSnapshotCommand orElse super.receiveCommand

  private def receiveSnapshotCommand: Receive = {
    case SaveSnapshotSuccess(metadata) =>
      log.info("Snapshot saved successfully {}", metadata)

    case SaveSnapshotFailure(metadata, reason) =>
      log.error(reason, "Snapshot save failed {}", metadata)
  }

  abstract override def receiveRecover: Receive = receiveSnapshotRecover orElse super.receiveRecover

  private def receiveSnapshotRecover: Receive = {
    case SnapshotOffer(_, aggregateSnapshot: AggregateSnapshot) =>
      setAggregateSnapshot(aggregateSnapshot)
  }

  def saveAggregateSnapshot(): Unit = {
    super.saveSnapshot(getAggregateSnapshot)
  }

  /**
    * Get the snapshot with the current aggregate state.
    */
  def getAggregateSnapshot: AggregateSnapshot = {
    AggregateSnapshot(
      revision,
      stateOption.map(getStateSnapshot),
      asAtLeastOnceDeliveryOption.map(_.getDeliverySnapshot),
      asReliableEventPublisherOption.map(_.getReliableEventPublisherSnapshot)
    )
  }

  /**
    * Use the snapshot to set the current aggregate state.
    */
  def setAggregateSnapshot(snapshot: AggregateSnapshot): Unit = {

    // Revision and state
    revisedState = RevisedState(
      snapshot.revision,
      snapshot.stateSnapshotOption.map { stateSnapshot =>
        getStateFromSnapshot(stateSnapshot.asInstanceOf[SnapshotType])
      }
    )

    // Reliable event publisher
    for {
      reliableEventPublisher <- asReliableEventPublisherOption
      reliableEventPublisherSnapshot <- snapshot.reliableEventPublisherSnapshotOption
    } {
      reliableEventPublisher.setReliableEventPublisherSnapshot(reliableEventPublisherSnapshot)
    }

    // At least once delivery
    for {
      atLeastOnceDelivery <- asAtLeastOnceDeliveryOption
      atLeastOnceDeliverySnapshot <- snapshot.atLeastOnceDeliverySnapshotOption
    } {
      val updatedUnconfirmedDeliveries: immutable.Seq[AtLeastOnceDelivery.UnconfirmedDelivery] = atLeastOnceDeliverySnapshot.unconfirmedDeliveries.map { unconfirmedDelivery =>

        val updatedMessageOption: Option[Any] = unconfirmedDelivery.message match {
          case publication@EventPublication(_, Some(confirmDeliveryRequest), _) =>

            // We need to update the target for the confirm delivery request as the actor ref has changed
            Some(publication.copy(confirmationOption = Some(confirmDeliveryRequest.copy(target = context.self))))

          case _ =>

            // Nothing to modify
            None
        }

        updatedMessageOption.fold(unconfirmedDelivery)(updatedMessage => unconfirmedDelivery.copy(message = updatedMessage))
      }

      atLeastOnceDelivery.setDeliverySnapshot(atLeastOnceDeliverySnapshot.copy(unconfirmedDeliveries = updatedUnconfirmedDeliveries))
    }
  }
}
