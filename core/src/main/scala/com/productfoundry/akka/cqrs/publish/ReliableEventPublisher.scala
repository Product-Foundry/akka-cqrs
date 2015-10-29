package com.productfoundry.akka.cqrs.publish

import akka.actor.{ActorLogging, ActorPath, ActorSystem}
import akka.persistence.{PersistentActor, AtLeastOnceDelivery}
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.messaging.Confirmable._

import scala.concurrent.duration._

/**
 * Reliably publishes commits to a target actor.
 *
 * A commit is only published after the previous published commit is confirmed to ensure aggregate
 * events are published in the right order, even in case of redelivery.
 */
trait ReliableEventPublisher
  extends EventPublisher
  with PersistentActor
  with AtLeastOnceDelivery
  with ActorLogging {

  this: Aggregate =>

  implicit def system: ActorSystem = context.system

  /**
   * Overwritten by [AtLeastOnceDelivery].
   */
  override def persistenceId: String = _persistenceId

  /**
   * Current publication that needs to be confirmed.
   */
  private var currentPublicationOption: Option[EventPublication] = None

  /**
   * Publications are queued until the current publication is confirmed.
   */
  private var pendingPublications: Vector[EventPublication] = Vector.empty

  /**
   * @return the target actor to publish to.
   */
  def publishTarget: ActorPath

  /**
   * Interval between redelivery attempts.
   */
  override def redeliverInterval: FiniteDuration = 10.seconds

  /**
   * Republishes all commits during recovery.
   *
   * Already confirmed published commits will not be published thanks to persisted Confirmed messages.
   */
  abstract override def receiveRecover: Receive = {

    case commit: Commit =>
      super.receiveRecover(commit)
      publishCommit(commit)

    case Confirmed(deliveryId) =>
      handleConfirmation(deliveryId)

    case event if super.receiveRecover.isDefinedAt(event) =>
      super.receiveRecover(event)
  }

  /**
   * Handles confirmation of published events.
   */
  abstract override def receiveCommand: Receive = {

    case Confirm(deliveryId) =>
      persist(Confirmed(deliveryId)) { _ =>
        handleConfirmation(deliveryId)
      }

    case command =>
      super.receiveCommand(command)
  }


  /**
   * Publishes a message.
   */
  override def publishMessage(eventPublication: EventPublication): Unit = {
    if (currentPublicationOption.isEmpty) {
      publishDirectly(eventPublication)
    } else {
      pendingPublications = pendingPublications :+ eventPublication
      log.debug("Pending publications: {}", pendingPublications.size)
    }
  }

  /**
   * Confirms the delivery.
   *
   * Also publishes next event if pending.
   *
   * @param deliveryId to confirm.
   */
  private def handleConfirmation(deliveryId: Long): Unit = {

    // Handle confirmation
    confirmDelivery(deliveryId)

    // There is no current publication anymore
    currentPublicationOption = None

    // If there are pending publications, publish the next one
    if (pendingPublications.nonEmpty) {
      publishDirectly(pendingPublications.head)
      pendingPublications = pendingPublications.tail
    }
  }

  /**
   * Publishes the event to the target actor.
   *
   * Also keeps track of the current unconfirmed event publication.
   *
   * @param eventPublication to publish.
   */
  private def publishDirectly(eventPublication: EventPublication): Unit = {
    deliver(publishTarget)(deliveryId => {
      assert(currentPublicationOption.isEmpty, "Unconfirmed publication pending")
      val publication = eventPublication.requestConfirmation(deliveryId)
      currentPublicationOption = Some(publication)
      publication
    })
  }
}
