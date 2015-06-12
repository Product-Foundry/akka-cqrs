package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.messaging.MessageSubscriber

/**
 * Indicates this actor receives event publications.
 */
trait EventSubscriber extends MessageSubscriber
