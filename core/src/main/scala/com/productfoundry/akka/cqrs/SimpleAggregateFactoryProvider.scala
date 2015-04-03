package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, ActorSystem}

import scala.reflect.ClassTag

class SimpleAggregateFactoryProvider(implicit system: ActorSystem, domainContext: DomainContext) extends AggregateFactoryProvider {

  /**
   * Creates aggregate supervisors for aggregates without s specialized constructor.
   */
  override def apply[A <: Aggregate[_] : ClassTag]: ActorRef = SimpleAggregateFactory.supervisor[A]
}
