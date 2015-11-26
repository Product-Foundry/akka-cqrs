package com.productfoundry.akka.cqrs.process

import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.productfoundry.contrib.pattern.ReceivePipeline
import com.productfoundry.akka.cqrs.publish.EventPublicationInterceptor

/**
  * Default receiveRecover that does nothing.
  */
trait NothingLeftToRecover {
  this: PersistentActor =>

  override def receiveRecover: Receive = {

    case _: SnapshotOffer =>
      throw new IllegalArgumentException("Override receiveRecover when using snapshots")

    case _: RecoveryCompleted =>

    case msg =>
      throw new IllegalArgumentException("Override receiveRecover when using persistent data")
  }
}

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
  with NothingLeftToRecover