package com.productfoundry.akka.cqrs

case class DomainCommit[+E <: AggregateEvent](revision: DomainRevision,
                                              commit: Commit[E]) extends Persistable

