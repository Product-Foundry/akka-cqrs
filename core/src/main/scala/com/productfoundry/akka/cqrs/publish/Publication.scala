package com.productfoundry.akka.cqrs.publish

import akka.actor.{Actor, ActorRef}
import com.productfoundry.akka.cqrs.Commit
import com.productfoundry.akka.cqrs.confirm.{Confirmable, Confirmation}

trait Publication extends Confirmable {

  override type self = Publication

  /**
   * @return The commit to publish.
   */
  def commit: Commit

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
                                           confirmationOption: Option[Confirmation] = None,
                                           commanderOption: Option[ActorRef] = None) extends Publication {

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): Publication = {
    copy(confirmationOption = Some(Confirmation(requester, deliveryId)))
  }

  override def includeCommander(commander: ActorRef): Publication = {
    copy(commanderOption = Some(commander))
  }

  override def notifyCommanderIfDefined(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    commanderOption.foreach(_ ! message)
  }
}