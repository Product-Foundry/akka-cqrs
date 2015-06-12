package com.productfoundry.akka.messaging

import akka.actor.Actor

/**
 * Indicates this actor handles published messages.
 */
trait MessageSubscriber extends Actor {

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    msg match {
      case confirmable: Confirmable => confirmable.confirmIfRequested()
      case _ =>
    }

    super.aroundReceive(receive, msg)
  }
}
