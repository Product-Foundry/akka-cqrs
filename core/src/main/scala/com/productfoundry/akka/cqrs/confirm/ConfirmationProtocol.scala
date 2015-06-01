package com.productfoundry.akka.cqrs.confirm

import com.productfoundry.akka.cqrs.Persistable
import play.api.libs.json.{Format, Reads, Writes}

/**
 * Can be used with AtLeastOnceDelivery.
 */
object ConfirmationProtocol {

  /**
   * Confirms a delivery.
   *
   * @param deliveryId of the delivered event.
   */
  case class Confirm(deliveryId: Long)

  /**
   * Confirmed delivery.
   *
   * Should be persisted as an event.
   *
   * @param deliveryId of the delivered event.
   */
  case class Confirmed(deliveryId: Long) extends Persistable

  object Confirmed {

    implicit val ConfirmedFormat: Format[Confirmed] = Format(Reads.of[Long].map(apply), Writes(a => Writes.of[Long].writes(a.deliveryId)))
  }

}
