package com.productfoundry.akka.cqrs.snapshot

import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot
import akka.persistence._
import com.productfoundry.akka.cqrs.{Aggregate, AggregateInternalException}
import com.productfoundry.akka.cqrs.publish.{EventPublication, ReliableEventPublisherSnapshot}

import scala.collection.immutable

trait AggregateSnapshotRecovery
  extends PersistentActor {

  this: Aggregate =>

  type StateSnapshotHandler = PartialFunction[Option[AggregateStateSnapshot], S]

  /**
    * Handles all saved snapshots.
    */
  def recoverStateFromSnapshot: StateSnapshotHandler

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

  /**
    * Saves a `snapshot` of this aggregate's state.
    *
    * The [[Aggregate]] will be notified about the success or failure of this
    * via an [[SaveSnapshotSuccess]] or [[SaveSnapshotFailure]] message.
    */
  override def saveSnapshot(snapshot: Any = None): Unit = {
    super.saveSnapshot(getAggregateSnapshot(snapshot))
  }

  /**
    * Get the snapshot with the current aggregate state.
    */
  def getAggregateSnapshot(snapshot: Any): AggregateSnapshot = {

    val stateSnapshotOption: Option[AggregateStateSnapshot] = snapshot match {
      case stateSnapshot: AggregateStateSnapshot => Some(stateSnapshot)
      case None => None
      case _ => throw AggregateInternalException("Provided class should be None or have trait AggregateStateSnapshot, also make sure you have a serializer for your AggregateStateSnapshot")
    }

    val atLeastOnceDeliverySnapshotOption: Option[AtLeastOnceDeliverySnapshot] = asAtLeastOnceDeliveryOption.map(_.getDeliverySnapshot)
    val reliableEventPublisherSnapshotOption: Option[ReliableEventPublisherSnapshot] = asReliableEventPublisherOption.map(_.getReliableEventPublisherSnapshot)

    AggregateSnapshot(
      revision,
      stateSnapshotOption,
      atLeastOnceDeliverySnapshotOption,
      reliableEventPublisherSnapshotOption
    )
  }

  /**
    * Use the snapshot to set the current aggregate state.
    */
  def setAggregateSnapshot(snapshot: AggregateSnapshot): Unit = {
    val stateSnapshotOption: Option[AggregateStateSnapshot] = snapshot.stateSnapshotOption

    if (recoverStateFromSnapshot.isDefinedAt(stateSnapshotOption)) {

      // Revision and state
      revisedState = RevisedState(snapshot.revision, Some(recoverStateFromSnapshot(stateSnapshotOption)))

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

    } else {
      val message: String = s"Unable to recover state from snapshot at revision: ${snapshot.revision}"
      log.error(message)
      throw AggregateInternalException(message)
    }
  }

}
