package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.Commit

trait EventProjection[P <: EventProjection[P]] extends Projection[P] with ContainerEventProjection[Commit, P] {
  self: P =>
}