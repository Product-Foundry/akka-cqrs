package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.AggregateTag

/**
 * Represents an update to a projection.
 * @param projectionId of the updated projection.
 * @param revision of the projection after the update.
 * @param tag of the update.
 */
case class ProjectionUpdate(projectionId: String, revision: ProjectionRevision, tag: AggregateTag)
