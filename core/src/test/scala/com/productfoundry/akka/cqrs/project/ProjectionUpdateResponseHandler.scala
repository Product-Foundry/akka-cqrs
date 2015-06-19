package com.productfoundry.akka.cqrs.project

import akka.actor.Actor

trait ProjectionUpdateResponseHandler extends ProjectionUpdateHandler {
  this: Projector with Actor =>

  /**
   * Handle a projected update.
   * @param update to handle.
   */
  override abstract def handleProjectedUpdate(update: ProjectionUpdate): Unit = {
    sender() ! update
    super.handleProjectedUpdate(update)
  }
}
