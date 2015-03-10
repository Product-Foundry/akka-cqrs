package com.productfoundry.akka.cqrs

/**
 * The revision of the domain when using the domain aggregator.
 */
case class DomainRevision(value: Long) extends Revision[DomainRevision]

object DomainRevision extends RevisionCompanion[DomainRevision]
