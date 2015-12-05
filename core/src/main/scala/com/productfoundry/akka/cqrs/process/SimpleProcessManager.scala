package com.productfoundry.akka.cqrs.process

import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Simple process manager that de-duplicates message to ensure that they are handled only once.
  *
  * If a message cannot be handled, processing that message will not be attempted again.
  *
  * If a process flow is more complex and needs to be resumed for example, consider using [[FsmProcessManager]].
  */
trait SimpleProcessManager
  extends ProcessManager
  with ReceivePipeline
  with EventPublicationInterceptor
  with DeduplicationInterceptor
