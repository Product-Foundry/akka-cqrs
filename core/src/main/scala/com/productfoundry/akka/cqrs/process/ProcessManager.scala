package com.productfoundry.akka.cqrs.process

import akka.actor.ActorLogging
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.Entity
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Process managers receive events and generates commands.
  */
trait ProcessManager
  extends Entity
  with ActorLogging {

  // Receive pipeline and interceptors always need to be mixed in last
  this: ReceivePipeline with EventPublicationInterceptor =>
}

/**
  * Process manager with minimal required behavior.
  *
  * Performs no auto deduplication. A way to do this would be to persist events
  */
trait DefaultProcessManager
  extends ProcessManager
  with ReceivePipeline
  with EventPublicationInterceptor

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


/**
  * Process manager with FSM support.
  *
  * Performs no auto deduplication. The recommended way to do this is through FSMState transitions.
  */
trait FsmProcessManager[S <: FSMState, D, E <: ProcessManagerEvent]
  extends ProcessManager
  with PersistentFSM[S, D, E]
  with ReceivePipeline
  with EventPublicationInterceptor