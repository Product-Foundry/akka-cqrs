package com.productfoundry.akka.cqrs

case class CommitHeaders(domainRevision: DomainRevision,
                         revision: AggregateRevision,
                         headers: Map[String, String] = Map.empty)