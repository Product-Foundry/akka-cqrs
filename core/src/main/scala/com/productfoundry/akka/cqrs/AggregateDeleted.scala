package com.productfoundry.akka.cqrs

case class AggregateDeleted(revision: AggregateRevision) extends DomainError