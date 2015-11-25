package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.Entity
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Process managers receive events and generates commands.
  */
trait ProcessManager
  extends Entity
  with ActorLogging {

  this: ReceivePipeline with EventPublicationInterceptor =>
}
