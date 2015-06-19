package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.publish.EventSubscriber

/**
 * Projects events onto a projection.
 */
trait Projector extends EventSubscriber with ProjectionUpdateHandler {

  /**
   * Uniquely identifies a projection created by the projector.
   */
  def projectionId: String

  /**
   * Default receive behavior.
   */
  override def receive: Receive = receivePublishedEvent

  /**
   * Partial function to handle published aggregate event records.
   */
  override def eventReceived: ReceiveEventRecord = project

  /**
   * Projects a single event record.
   */
  def project: ReceiveEventRecord

  /**
   * Handle a projected update.
   * @param update to handle.
   */
  override def handleProjectedUpdate(update: ProjectionUpdate): Unit = {}
}
