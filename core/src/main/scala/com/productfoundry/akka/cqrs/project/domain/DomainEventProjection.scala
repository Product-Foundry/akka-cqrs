package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.project.ContainerEventProjection

trait DomainEventProjection[P <: DomainEventProjection[P]] extends DomainProjection[P] with ContainerEventProjection[DomainCommit, P] {
  self: P =>
}