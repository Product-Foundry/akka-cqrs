package com.productfoundry.akka.messaging

import akka.actor.Actor

trait MessagePublisher {
  this: Actor =>

  /**
   * Publishes a message.
   */
  def publishMessage(message: Any): Unit
}
