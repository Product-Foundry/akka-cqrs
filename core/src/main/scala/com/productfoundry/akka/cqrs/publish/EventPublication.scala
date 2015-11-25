package com.productfoundry.akka.cqrs.publish

import akka.actor.ActorRef
import com.productfoundry.akka.cqrs.{AggregateEventRecord, EntityMessage}
import com.productfoundry.akka.messaging.{ConfirmDeliveryRequest, Confirmable}

trait EventPublication extends Confirmable with EntityMessage {

  override type self = EventPublication

  /**
   * @return The event record to publish.
   */
  def eventRecord: AggregateEventRecord
}

/**
 * Event publication companion.
 */
object EventPublication {

  /**
   * Create publication for an event.
   * @param eventRecord to publish.
   * @return Event publication.
   */
  def apply(eventRecord: AggregateEventRecord): EventPublication = EventPublicationImpl(eventRecord)
}

private[this] case class EventPublicationImpl(eventRecord: AggregateEventRecord,
                                              confirmationOption: Option[ConfirmDeliveryRequest] = None,
                                              commanderOption: Option[ActorRef] = None) extends EventPublication {

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): EventPublication = {
    copy(confirmationOption = Some(ConfirmDeliveryRequest(requester, deliveryId)))
  }
}