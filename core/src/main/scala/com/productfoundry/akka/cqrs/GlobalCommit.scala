package com.productfoundry.akka.cqrs

case class GlobalCommit[+Event <: DomainEvent](revision: GlobalRevision, timestamp: Long, commit: Commit[Event])
