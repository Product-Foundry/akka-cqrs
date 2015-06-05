package com.productfoundry.akka.cqrs

/**
 * Uniquely identifies an aggregate revision.
 */
case class AggregateSnapshot(name: String, id: String, revision: AggregateRevision)