package com.productfoundry.akka.cqrs

import akka.actor.ActorRef
import com.productfoundry.akka.messaging.{ConfirmDeliveryRequest, Confirmable}

case class DummyConfirmable(value: Long, confirmationOption: Option[ConfirmDeliveryRequest] = None) extends Confirmable {

  override type self = DummyConfirmable

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self = {
    copy(confirmationOption = Some(ConfirmDeliveryRequest(requester, deliveryId)))
  }
}
