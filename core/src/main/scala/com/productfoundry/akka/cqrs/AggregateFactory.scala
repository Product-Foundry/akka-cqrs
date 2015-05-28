package com.productfoundry.akka.cqrs

/**
 * creates an aggregate.
 * @tparam A Aggregate type.
 */
trait AggregateFactory[A <: Aggregate] extends EntityFactory[A]
