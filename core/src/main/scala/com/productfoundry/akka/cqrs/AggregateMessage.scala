package com.productfoundry.akka.cqrs

trait AggregateMessage extends EntityMessage {
  type Id <: AggregateId
}