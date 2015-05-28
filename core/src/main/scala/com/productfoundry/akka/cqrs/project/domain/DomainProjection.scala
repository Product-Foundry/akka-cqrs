package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.project.ContainerProjection

/**
 * Defines a domain projection.
 *
 * @tparam R projection result type
 */
trait DomainProjection[R] extends ContainerProjection[DomainCommit, R]
