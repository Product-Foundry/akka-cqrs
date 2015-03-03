package com.productfoundry.akka.cqrs

import akka.actor.ActorPath

/**
 * Wrapper around persistence id.
 * @param value representing the persistence id.
 */
case class PersistenceId(value: String)

object PersistenceId {
  /**
   * Creates a persistence id based on the actor path name.
   *
   * Uses the entire name after the user segment for the persistence id.
   *
   * @param actorPath to determine persistenceId.
   * @return Persistence id.
   */
  def apply(actorPath: ActorPath): PersistenceId = {
    actorPath.toStringWithoutAddress match {
      case PersistenceIdPathPattern(value) => PersistenceId(value)
      case path => throw new IllegalArgumentException(s"Unexpected path: $path")
    }
  }

  /**
   * Used to generate the persistenceId based on the actor path.
   */
  val PersistenceIdPathPattern = "/user/(.+)".r
}