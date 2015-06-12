package com.productfoundry.akka.messaging

import akka.actor.Actor

/**
 * Ensures confirmations are sent.
 */
trait ConfirmationHandler extends Actor {

  /**
   * Sends a confirmation if requested for confirmables.
   */
  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    msg match {
      case c: Confirmable => c.confirmIfRequested()
      case _ =>
    }

    super.aroundReceive(receive, msg)
  }
}
