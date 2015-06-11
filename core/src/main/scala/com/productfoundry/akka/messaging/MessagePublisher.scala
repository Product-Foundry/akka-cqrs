package com.productfoundry.akka.messaging

import akka.actor.Actor

trait MessagePublisher[T] {
  this: Actor =>

  /**
   * Publishes a message.
   */
  def publishMessage(message: T): Unit
}
