package com.productfoundry.akka.cqrs

case class DomainCommit[+Event <: DomainEvent](revision: DomainRevision, timestamp: Long, commit: Commit[Event])
