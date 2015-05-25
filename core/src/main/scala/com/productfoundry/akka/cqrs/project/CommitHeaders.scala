package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.AggregateRevision

case class CommitHeaders(domainRevision: DomainRevision,
                         revision: AggregateRevision,
                         timestamp: Long,
                         headers: Map[String, String] = Map.empty)