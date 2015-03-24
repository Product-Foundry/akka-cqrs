package com.productfoundry.akka.cqrs

/**
 * Build projections.
 */
trait Projection[P] {

  type Project = PartialFunction[AggregateEvent, P]

  def project(revision: AggregateRevision): Project

  def projectOnto[C <: Projection[C]](revision: AggregateRevision, event: AggregateEvent, projection: C): C = {
    val projectFunction = projection.project(revision)
    if (projectFunction.isDefinedAt(event)) projectFunction(event) else projection
  }
}