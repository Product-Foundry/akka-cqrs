package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.serialization.Persistable

@deprecated("use Persistence Query instead", "0.1.28")
case class DomainAggregatorSnapshot(revision: ProjectionRevision) extends Persistable
