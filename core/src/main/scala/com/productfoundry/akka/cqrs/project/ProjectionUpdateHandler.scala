package com.productfoundry.akka.cqrs.project

/**
 * Mixin for aggregates to handle persisted commits.
 */
trait ProjectionUpdateHandler {
  this: Projector =>

  /**
   * Handle a projected update.
   * @param update to handle.
   */
  def handleProjectedUpdate(update: ProjectionUpdate): Unit
}
