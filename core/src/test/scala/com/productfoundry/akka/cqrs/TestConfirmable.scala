package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

case class TestConfirmable(value: Long, confirmationOption: Option[Confirmation] = None) extends Confirmable {

  override type self = TestConfirmable

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): self = {
    copy(confirmationOption = Some(Confirmation(requester, deliveryId)))
  }
}
