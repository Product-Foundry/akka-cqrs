package com.productfoundry.akka.cqrs.project.domain

trait DomainProjectionProvider[P <: DomainProjection[P]] {

  /**
   * @return the projection.
   */
  def get: P

  /**
   * The projection with the minimum revision.
   *
   * @param minimum revision.
   * @return state with actual revision, where actual >= minimum.
   */
  def getWithRevision(minimum: DomainRevision): (P, DomainRevision)
}
