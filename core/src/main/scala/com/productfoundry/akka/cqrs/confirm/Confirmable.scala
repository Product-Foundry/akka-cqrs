package com.productfoundry.akka.cqrs.confirm

import akka.actor.ActorRef
import com.productfoundry.akka.cqrs.confirm.ConfirmationProtocol.Confirm

trait Confirmable {

  type self <: Confirmable

  /**
   * Contains confirmation details in case we requested confirmation.
   */
  def confirmationOption: Option[Confirmation]

  /**
   * Creates a new confirmable with a confirmation request.
   */
  def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self

  /**
   * Confirm delivery if requested.
   */
  def confirmIfRequested(): Unit = {
    confirmationOption.foreach { confirmation =>
      confirmation.target ! Confirm(confirmation.deliveryId)
    }
  }
}
