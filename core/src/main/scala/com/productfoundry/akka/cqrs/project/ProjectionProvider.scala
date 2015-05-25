package com.productfoundry.akka.cqrs.project

trait ProjectionProvider[P] {

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
