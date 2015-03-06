package com.productfoundry.akka.cqrs

case class DomainAggregatorFailed(revision: AggregateRevision) extends AggregateError
