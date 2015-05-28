package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{AggregateEvent, Commit, Persistable}

case class DomainCommit[+E <: AggregateEvent](revision: DomainRevision,
                                              timestamp: Long,
                                              commit: Commit[E]) extends Persistable

