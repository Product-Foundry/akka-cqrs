package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.serialization.Persistable

case class DomainRevisionSnapshot(revision: ProjectionRevision) extends Persistable
