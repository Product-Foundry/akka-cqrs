package com.productfoundry.akka.messaging

/**
 * Indicates this actor handles published messages.
 */
trait MessageSubscriber extends ConfirmationHandler with DeduplicationHandler