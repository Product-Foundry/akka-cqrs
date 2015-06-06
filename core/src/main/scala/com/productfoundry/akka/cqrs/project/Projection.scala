package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.AggregateEventRecord

/**
 * Defines a projection.
 *
 * @tparam R projection result type
 */
trait Projection[R] {

  /**
   * Projects a single event record.
   */
  def project(eventRecord: AggregateEventRecord): R
}
