package com.productfoundry.akka.cqrs.publish

import akka.actor.{Actor, ActorRef}
import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.messaging.{Deduplicatable, Confirmable}
import com.productfoundry.akka.messaging.Confirmable._

trait EventPublication extends Confirmable with Deduplicatable {

  override type self = EventPublication

  /**
   * @return The event record to publish.
   */
  def eventRecord: AggregateEventRecord

  /**
   * Includes the commander, which can be used to send additional info when handling the published event record.
   *
   * The commander is probably never available during recovery.
   *
   * @param commander that send the command to the aggregate.
   * @return Updated commit publication that includes the commander.
   */
  def includeCommander(commander: ActorRef): EventPublication

  /**
   * Notifies the commander if it is defined.
   *
   * @param message with the notification.
   */
  def notifyCommanderIfDefined(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit

  /**
   * Used for deduplication.
   */
  override def deduplicationId: String = eventRecord.tag.value
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
                                              confirmationOption: Option[ConfirmationRequest] = None,
                                              commanderOption: Option[ActorRef] = None) extends EventPublication {

  override def requestConfirmation(deliveryId: Long)(implicit requester: ActorRef): EventPublication = {
    copy(confirmationOption = Some(ConfirmationRequest(requester, deliveryId)))
  }

  override def includeCommander(commander: ActorRef): EventPublication = {
    copy(commanderOption = Some(commander))
  }

  override def notifyCommanderIfDefined(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    commanderOption.foreach(_ ! message)
  }
}