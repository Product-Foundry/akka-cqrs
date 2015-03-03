package com.productfoundry.akka.cqrs

import akka.actor.ActorPath

/**
 * All aggregates have identity.
 */
case class AggregateId(uuid: Uuid) extends EntityId

object AggregateId extends EntityIdCompanion[AggregateId] {

  /**
   * Construct an aggregate id based on the actor path.
   * @param path to the actor.
   * @return aggregate id.
   */
  def apply(path: ActorPath): AggregateId = {
    apply(path.elements.lastOption.getOrElse(throw new IllegalArgumentException(s"Unexpected path: ${path.toStringWithoutAddress}")))
  }
}