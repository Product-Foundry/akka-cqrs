package com.productfoundry.akka.cqrs

import akka.actor.{ActorLogging, ActorPath, ActorSystem}
import akka.persistence.AtLeastOnceDelivery.UnconfirmedWarning
import akka.persistence.{PersistentActor, AtLeastOnceDelivery}
import com.productfoundry.akka.cqrs.ConfirmationProtocol._

import scala.concurrent.duration._

/**
 * Reliably publishes commits to a target actor.
 */
trait ReliableCommitPublisher extends PersistentActor with CommitPublisher with AtLeastOnceDelivery with ActorLogging {
  this: Aggregate[_] =>
  
  implicit def system: ActorSystem = context.system

  /**
   * @return the target actor to publish to.
   */
  def publishTarget: ActorPath

  override def redeliverInterval: FiniteDuration = 10.seconds

  override def warnAfterNumberOfUnconfirmedAttempts: Int = 30

  /**
   * Republishes all commits during recovery.
   *
   * Already confirmed published commits will not be handled published thanks to persisted Confirmed messages.
   */
  abstract override def receiveRecover: Receive = {
    case commit: Commit[_] =>
      super.receiveRecover(commit)
      publishCommit(commit)

    case Confirmed(deliveryId) =>
      confirmDelivery(deliveryId)

    case event =>
      super.receiveRecover(event)
  }

  /**
   * Handles confirmation of published events.
   */
  abstract override def receiveCommand: Receive = {
    case Confirm(deliveryId) =>
      persist(Confirmed(deliveryId)) {
        _ => confirmDelivery(deliveryId)
      }

    case UnconfirmedWarning(unconfirmedDeliveries) =>
      log.warning("Unconfirmed: {}", unconfirmedDeliveries)

    case command =>
      super.receiveCommand(command)
  }

  /**
   * Reliably publishes a persisted commit.
   */
  override def publishCommit(commit: Commit[AggregateEvent]): Unit = {
    deliver(publishTarget, deliveryId => CommitPublication(commit).requestConfirmation(deliveryId))
  }
}

