package com.productfoundry.akka.cqrs

case class CommitHeaders(domainRevision: DomainRevision,
                         revision: AggregateRevision,
                         timestamp: Long,
                         headers: Map[String, String] = Map.empty)