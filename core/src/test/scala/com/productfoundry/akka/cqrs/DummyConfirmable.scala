package com.productfoundry.akka.cqrs

import akka.actor.ActorRef
import com.productfoundry.akka.messaging.Confirmable
import com.productfoundry.akka.messaging.Confirmable._

case class DummyConfirmable(value: Long, confirmationOption: Option[ConfirmationRequest] = None) extends Confirmable {

  override type self = DummyConfirmable

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self = {
    copy(confirmationOption = Some(ConfirmationRequest(requester, deliveryId)))
  }
}
