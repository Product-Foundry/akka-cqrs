package com.productfoundry.akka.cqrs

trait AggregateMessage extends EntityMessage {
  def aggregateId: AggregateId

  override def entityId: EntityId = aggregateId
}
