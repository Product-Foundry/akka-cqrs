package com.productfoundry.akka.messaging

import akka.actor.ActorRef
import com.productfoundry.akka.serialization.Persistable

/**
  * Indicates a message can be confirmed.
  */
trait Confirmable {

  type self <: Confirmable

  /**
    * Contains confirmation details in case we requested confirmation.
    */
  def confirmationOption: Option[ConfirmDeliveryRequest]

  /**
    * Creates a new confirmable with a confirmation request.
    */
  def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self

  /**
    * Confirm delivery if requested.
    */
  def confirmIfRequested(): Unit = {
    confirmationOption.foreach { confirmation =>
      confirmation.target ! ConfirmDelivery(confirmation.deliveryId)
    }
  }
}

/**
  * Use to store information required in the confirm message.
  *
  * @param target     to receive the confirmation.
  * @param deliveryId to confirm.
  */
case class ConfirmDeliveryRequest(target: ActorRef, deliveryId: Long) extends Persistable

/**
  * Confirms a delivery.
  *
  * @param deliveryId of the delivered event.
  */
case class ConfirmDelivery(deliveryId: Long)

/**
  * Confirmed delivery.
  *
  * Should be persisted as an event.
  *
  * @param deliveryId of the delivered event.
  */
case class ConfirmedDelivery(deliveryId: Long) extends Persistable
