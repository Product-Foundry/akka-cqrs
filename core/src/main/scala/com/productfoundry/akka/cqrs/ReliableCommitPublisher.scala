package com.productfoundry.akka.cqrs

import akka.actor.{ActorLogging, ActorPath, ActorSystem}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import com.productfoundry.akka.cqrs.ConfirmationProtocol._

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
  private var currentPublicationOption: Option[CommitPublication[AggregateEvent]] = None

  /**
   * Publications are queued until the current publication is confirmed.
   */
  private var pendingPublications: Vector[Commit[AggregateEvent]] = Vector.empty

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
      assert(currentPublicationOption.flatMap(_.deliveryIdOption).contains(deliveryId), "Unexpected delivery id")

      // Persist confirmation
      persist(Confirmed(deliveryId)) { _ =>

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

    case command =>
      super.receiveCommand(command)
  }

  /**
   * Reliably publishes a persisted commit.
   */
  override def publishCommit(commit: Commit[AggregateEvent]): Unit = {
    if (currentPublicationOption.isEmpty) {
      publishDirectly(commit)
    } else {
      pendingPublications = pendingPublications :+ commit

      log.debug("Pending publications: {}", pendingPublications.size)
    }
  }

  /**
   * Publishes the commit to the target actor.
   *
   * Also keeps track of the current unconfirmed commit publication.
   *
   * @param commit to publish.
   */
  private def publishDirectly(commit: Commit[AggregateEvent]): Unit = {
    deliver(publishTarget, deliveryId => {
      assert(currentPublicationOption.isEmpty, "Unconfirmed publication pending")
      val publication = CommitPublication(commit).requestConfirmation(deliveryId)
      currentPublicationOption = Some(publication)
      publication
    })
  }
}
