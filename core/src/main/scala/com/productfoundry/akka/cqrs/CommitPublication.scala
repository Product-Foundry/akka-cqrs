package com.productfoundry.akka.cqrs

import akka.actor.ActorRef
import com.productfoundry.akka.cqrs.ConfirmationProtocol.Confirm

trait CommitPublication[+E <: AggregateEvent] {
  def commit: Commit[AggregateEvent]

  def deliveryIdOption: Option[Long]

  def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef) : CommitPublication[E]

  def confirmIfRequested(): Unit
}

/**
 * Companion.
 */
object CommitPublication {

  /**
   * Create commit publication.
   * @param commit to publish.
   * @tparam E Base type of events in the commit.
   * @return Commit publication.
   */
  def apply[E <: AggregateEvent](commit: Commit[E]): CommitPublication[E] = CommitPublicationImpl(commit)
}

private[this] case class CommitPublicationImpl[E <: AggregateEvent](commit: Commit[E], requestedConfirmationOption: Option[(ActorRef, Long)] = None) extends CommitPublication[E] {

  override def deliveryIdOption: Option[Long] = requestedConfirmationOption.map(_._2)

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): CommitPublication[E] = {
    copy(requestedConfirmationOption = Some(requester -> deliveryId))
  }

  override def confirmIfRequested(): Unit = {
    requestedConfirmationOption.foreach {
      case (requester, deliveryId) => requester ! Confirm(deliveryId)
    }
  }
}