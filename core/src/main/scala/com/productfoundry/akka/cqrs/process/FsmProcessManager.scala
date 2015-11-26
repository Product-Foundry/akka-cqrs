package com.productfoundry.akka.cqrs.process

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Process manager with FSM support.
  *
  * Performs no auto deduplication.
  */
trait FsmProcessManager[S <: FSMState, D, E]
  extends ProcessManager
  with PersistentFSM[S, D, E]
  with ReceivePipeline
  with EventPublicationInterceptor