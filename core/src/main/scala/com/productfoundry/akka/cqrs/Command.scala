package com.productfoundry.akka.cqrs

/**
 * Base command marker trait.
 */
trait Command extends EntityMessage

/**
 * Command message.
 *
 * @param expected revision.
 * @param command to execute.
 */
case class CommandMessage(expected: AggregateRevision, command: Command) extends EntityMessage {
  override def entityId: EntityId = command.entityId
}