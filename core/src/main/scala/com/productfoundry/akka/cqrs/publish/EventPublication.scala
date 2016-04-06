package com.productfoundry.akka.cqrs.publish

import akka.actor.ActorRef
import com.productfoundry.akka.cqrs.{AggregateEventRecord, EntityMessage}
import com.productfoundry.akka.messaging.{ConfirmDeliveryRequest, Confirmable}
import com.productfoundry.akka.serialization.Persistable

case class EventPublication(eventRecord: AggregateEventRecord,
                            confirmationOption: Option[ConfirmDeliveryRequest] = None,
                            commanderOption: Option[ActorRef] = None)
  extends Confirmable
    with EntityMessage
    with Persistable {

  override type self = EventPublication

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): EventPublication = {
    copy(confirmationOption = Some(ConfirmDeliveryRequest(requester, deliveryId)))
  }
}
