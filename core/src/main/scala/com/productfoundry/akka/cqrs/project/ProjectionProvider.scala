package com.productfoundry.akka.cqrs.project

import scala.concurrent.Future

/**
 * Provides access to a projection with a specific version.
 */
trait ProjectionProvider[P <: Projection] {

  type StateWithRevision = (P, ProjectionRevision)

  /**
   * @return the projection.
   */
  def get: Future[StateWithRevision] = getWithRevision(ProjectionRevision.Initial)

  /**
   * The projection with the minimum revision.
   *
   * @param minimum revision.
   * @return state with actual revision, where actual >= minimum.
   */
  def getWithRevision(minimum: ProjectionRevision): Future[StateWithRevision]
}
