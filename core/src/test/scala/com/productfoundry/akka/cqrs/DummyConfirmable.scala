package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

case class DummyConfirmable(value: Long, confirmationOption: Option[Confirmation] = None) extends Confirmable {

  override type self = DummyConfirmable

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self = {
    copy(confirmationOption = Some(Confirmation(requester, deliveryId)))
  }
}
