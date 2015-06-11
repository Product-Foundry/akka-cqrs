package com.productfoundry.akka.messaging

import akka.actor.Actor

/**
 * Indicates this actor handles published messages.
 */
trait MessageSubscriber {
  this: Actor =>

  /**
   * Handles a published message.
   */
  def handlePublishedMessage: Receive
}
