package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.messaging.MessageSubscriber

/**
 * Indicates this actor receives event publications.
 */
trait EventSubscriber extends MessageSubscriber {

  type ReceiveEventRecord = PartialFunction[AggregateEventRecord, Unit]

  /**
   * Can be used as default receive function to handle published events.
   */
  def receivePublishedEvent: Receive = {
    case publication: EventPublication =>
      publication.confirmIfRequested()
      eventReceived.applyOrElse(publication.eventRecord, unhandled)

    case eventRecord: AggregateEventRecord if eventReceived.isDefinedAt(eventRecord) =>
      eventReceived(eventRecord)
  }

  /**
   * Partial function to handle published aggregate event records.
   */
  def eventReceived: ReceiveEventRecord = PartialFunction.empty
}
