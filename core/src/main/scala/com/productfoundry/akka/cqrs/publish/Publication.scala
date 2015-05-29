package com.productfoundry.akka.cqrs.publish

import akka.actor.{Actor, ActorRef}
import com.productfoundry.akka.cqrs.Commit
import com.productfoundry.akka.cqrs.publish.ConfirmationProtocol.Confirm

trait Publication {

  /**
   * @return The commit to publish.
   */
  def commit: Commit

  /**
   * Request confirmation for this publication.
   *
   * @param deliveryId to keep track of confirmations.
   * @param requester to receive the confirmation.
   *
   * @return Updated commit publication that can send back confirmations.
   */
  def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): Publication

  /**
   * @return Optional delivery id for confirmation.
   */
  def deliveryIdOption: Option[Long]

  /**
   * Confirm delivery if requested.
   */
  def confirmIfRequested(): Unit

  /**
   * Includes the commander, which can be used to send additional info when handling the published commit.
   * @param commander to receive additional publication info.
   *
   * @return Updated commit publication that includes the commander.
   */
  def includeCommander(commander: ActorRef): Publication

  /**
   * Notifies the commander if it is known.
   *
   * @param message with the notification.
   */
  def notifyCommanderIfDefined(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit
}

/**
 * Companion.
 */
object Publication {

  /**
   * Create commit publication.
   * @param commit to publish.
   * @return Commit publication.
   */
  def apply(commit: Commit): Publication = CommitPublication(commit)
}

private[this] case class CommitPublication(commit: Commit,
                                           requestedConfirmationOption: Option[(ActorRef, Long)] = None,
                                           commanderOption: Option[ActorRef] = None) extends Publication {

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): Publication = {
    copy(requestedConfirmationOption = Some(requester -> deliveryId))
  }

  override def deliveryIdOption: Option[Long] = requestedConfirmationOption.map(_._2)

  override def confirmIfRequested(): Unit = {
    requestedConfirmationOption.foreach {
      case (requester, deliveryId) => requester ! Confirm(deliveryId)
    }
  }

  override def includeCommander(commander: ActorRef): Publication = {
    copy(commanderOption = Some(commander))
  }

  override def notifyCommanderIfDefined(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    commanderOption.foreach(_ ! message)
  }
}