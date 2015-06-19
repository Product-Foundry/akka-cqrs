package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{Revision, RevisionCompanion}

/**
 * The revision of a projection.
 */
case class ProjectionRevision(value: Long) extends Revision[ProjectionRevision]

object ProjectionRevision extends RevisionCompanion[ProjectionRevision]
