package com.productfoundry.akka.messaging

import akka.actor.Actor

/**
 * Indicates this actor handles published messages.
 */
trait MessageSubscriber extends Actor with ConfirmationHandler