package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.{Projection, ProjectionRevision}

/**
 * Defines a projection.
 *
 * @tparam R projection result type
 */
@deprecated("use Persistence Query instead", "0.1.28")
trait DomainProjection[R] extends Projection {

  /**
   * Projects a single event record.
   */
  def project(revision: ProjectionRevision, eventRecord: AggregateEventRecord): R
}
