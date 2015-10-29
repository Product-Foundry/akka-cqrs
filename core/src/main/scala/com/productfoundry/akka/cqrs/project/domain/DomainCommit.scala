package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.serialization.Persistable

/**
 * A successful aggregated event record.
 *
 * @param revision of the domain aggregator.
 * @param eventRecord that was aggregated.
 */
@deprecated("use Persistence Query instead", "0.1.28")
case class DomainCommit(revision: ProjectionRevision, eventRecord: AggregateEventRecord) extends Persistable