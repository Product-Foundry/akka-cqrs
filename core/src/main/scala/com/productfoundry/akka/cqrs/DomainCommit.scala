package com.productfoundry.akka.cqrs

case class DomainCommit[+E <: AggregateEvent](revision: DomainRevision,
                                              timestamp: Long,
                                              commit: Commit[E])
