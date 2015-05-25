package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.AggregateEvent

/**
 * Build projections.
 */
trait Projection[P <: Projection[P]] {

  self: P =>

  def project(headers: CommitHeaders, events: Seq[AggregateEvent]): P = events.foldLeft(this)(_.project(headers, _))

  def project(headers: CommitHeaders, event: AggregateEvent): P
}
