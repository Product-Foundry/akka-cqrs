package com.productfoundry.akka.cqrs.publish

import akka.actor.{ActorLogging, ActorPath, ActorSystem}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import com.productfoundry.akka.cqrs.publish.ConfirmationProtocol._
import com.productfoundry.akka.cqrs.{Aggregate, Commit}

import scala.concurrent.duration._

/**
 * Reliably publishes commits to a target actor.
 *
 * A commit is only published after the previous published commit is confirmed to ensure aggregate
 * events are published in the right order, even in case of redelivery.
 */
trait ReliableCommitPublisher extends PersistentActor with CommitPublisher with AtLeastOnceDelivery with ActorLogging {
  this: Aggregate[_] =>

  implicit def system: ActorSystem = context.system

  /**
   * Current publication that needs to be confirmed.
   */
  private var currentPublicationOption: Option[Publication] = None

  /**
   * Publications are queued until the current publication is confirmed.
   */
  private var pendingPublications: Vector[Publication] = Vector.empty

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
      publishCommit(Publication(commit))

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
   * Publish to the target actor if no publications are pending, otherwise enqueue.
   * @param commitPublication to publish.
   */
  override def publishCommit(commitPublication: Publication): Unit = {
    if (currentPublicationOption.isEmpty) {
      publishDirectly(commitPublication)
    } else {
      pendingPublications = pendingPublications :+ commitPublication
      log.debug("Pending publications: {}", pendingPublications.size)
    }
  }

  /**
   * Confirms the delivery.
   *
   * Also publishes next commit if pending.
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
   * Publishes the commit to the target actor.
   *
   * Also keeps track of the current unconfirmed commit publication.
   *
   * @param commitPublication to publish.
   */
  private def publishDirectly(commitPublication: Publication): Unit = {
    deliver(publishTarget, deliveryId => {
      assert(currentPublicationOption.isEmpty, "Unconfirmed publication pending")
      val publication = commitPublication.requestConfirmation(deliveryId)
      currentPublicationOption = Some(publication)
      publication
    })
  }
}
