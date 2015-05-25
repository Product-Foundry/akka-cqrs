package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{Revision, RevisionCompanion}

/**
 * The revision of the domain when using the domain aggregator.
 */
case class DomainRevision(value: Long) extends Revision[DomainRevision] {
  override def next: DomainRevision = DomainRevision(value + 1L)
}

object DomainRevision extends RevisionCompanion[DomainRevision]
