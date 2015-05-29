package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.project.Projection

import scala.concurrent.Future

trait DomainProjectionProvider[P <: Projection[P]] {

  type StateWithRevision = (P, DomainRevision)

  /**
   * @return the projection.
   */
  def get: Future[StateWithRevision] = getWithRevision(DomainRevision.Initial)

  /**
   * The projection with the minimum revision.
   *
   * @param minimum revision.
   * @return state with actual revision, where actual >= minimum.
   */
  def getWithRevision(minimum: DomainRevision): Future[StateWithRevision]
}
