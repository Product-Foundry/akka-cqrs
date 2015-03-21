package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

import scala.reflect.ClassTag

trait AggregateFactoryProvider {

  def apply[A <: Aggregate[_, _] : ClassTag]: ActorRef
}
