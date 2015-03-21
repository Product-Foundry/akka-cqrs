package com.productfoundry.akka.cqrs

/**
 * Build memory projections.
 */
trait MemoryProjection[P] {

  type Project = PartialFunction[AggregateEvent, P]

  def project(revision: AggregateRevision): Project

  def projectOnto[C <: MemoryProjection[C]](revision: AggregateRevision, event: AggregateEvent, projection: C): Option[C] = {
    val projectFunction = projection.project(revision)
    if (projectFunction.isDefinedAt(event)) Some(projectFunction(event)) else None
  }
}